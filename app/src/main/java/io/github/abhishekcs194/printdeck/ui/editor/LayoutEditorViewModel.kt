package io.github.abhishekcs194.printdeck.ui.editor

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.abhishekcs194.printdeck.core.model.ImpositionMode
import io.github.abhishekcs194.printdeck.core.model.ImpositionSettings
import io.github.abhishekcs194.printdeck.data.LoadedDocument
import io.github.abhishekcs194.printdeck.pdf.engine.ImpositionEngine
import io.github.abhishekcs194.printdeck.pdf.engine.PdfPreviewRenderer
import io.github.abhishekcs194.printdeck.pdf.imposition.ImpositionPlan
import io.github.abhishekcs194.printdeck.pdf.imposition.Imposer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LayoutEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: ImpositionEngine,
    private val previewRenderer: PdfPreviewRenderer,
) : ViewModel() {

    data class UiState(
        val document: LoadedDocument? = null,
        val settings: ImpositionSettings = ImpositionSettings(),
        val plan: ImpositionPlan? = null,
        val previewIndex: Int = 0,
        /**
         * Rendered sheets, keyed by index. Cached so swiping back to a sheet is
         * instant and neighbours can be prefetched — a pager that blanks between
         * pages feels broken however fast each render is.
         */
        val previews: Map<Int, Bitmap> = emptyMap(),
        val rendering: Boolean = false,
        val error: String? = null,
    ) {
        val sheetCount: Int get() = plan?.sheetCount ?: 0

        fun previewOf(index: Int): Bitmap? = previews[index]

        /** "8 pages onto 2 sheets" — the number people actually care about. */
        val summary: String
            get() {
                val pages = document?.pageCount ?: 0
                val sheets = sheetCount
                return "${pages.plural("page")} onto ${sheets.plural("sheet")}"
            }

        private fun Int.plural(noun: String) = if (this == 1) "1 $noun" else "$this ${noun}s"
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var previewJob: Job? = null

    fun setDocument(document: LoadedDocument) {
        if (_state.value.document?.file == document.file) return
        _state.update { it.copy(document = document, previewIndex = 0) }
        replan()
    }

    fun updateSettings(transform: (ImpositionSettings) -> ImpositionSettings) {
        _state.update { it.copy(settings = transform(it.settings)) }
        replan()
    }

    /** Switches layout mode, keeping shared settings such as paper and margins. */
    fun setMode(mode: ImpositionMode) = updateSettings { it.copy(mode = mode) }

    fun showSheet(index: Int) {
        val count = _state.value.sheetCount
        if (count == 0) return
        _state.update { it.copy(previewIndex = index.coerceIn(0, count - 1)) }
        renderPreview()
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    /**
     * Recomputes the plan, then the preview.
     *
     * Planning is pure arithmetic and effectively free, so it happens on every
     * keystroke and the sheet count updates instantly. Only the preview, which
     * writes and renders a PDF, is debounced.
     */
    private fun replan() {
        val state = _state.value
        val document = state.document ?: return

        val plan = runCatching { Imposer.plan(document.pageSizes, state.settings) }
            .getOrElse { failure ->
                _state.update { it.copy(error = failure.message ?: "That layout is not possible.") }
                return
            }

        _state.update {
            it.copy(
                plan = plan,
                error = null,
                // Every cached sheet was drawn under the old settings.
                previews = emptyMap(),
                previewIndex = it.previewIndex.coerceAtMost((plan.sheetCount - 1).coerceAtLeast(0)),
            )
        }
        renderPreview()
    }

    private fun renderPreview() {
        val snapshot = _state.value
        val document = snapshot.document ?: return
        val plan = snapshot.plan ?: return
        if (plan.sheets.isEmpty()) return

        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            // Settings arrive in bursts while a control is being dragged. Waiting
            // out the burst avoids imposing a document per frame. Swipes are not
            // debounced, because a page that lags behind the finger feels broken.
            if (snapshot.previews.isEmpty()) delay(DEBOUNCE_MS)
            _state.update { it.copy(rendering = true) }

            runCatching {
                render(document, plan, snapshot.previewIndex)

                // Prefetch either side so a swipe lands on a drawn sheet. Done
                // after the visible one, so it never delays what is on screen.
                listOf(snapshot.previewIndex - 1, snapshot.previewIndex + 1)
                    .filter { it in plan.sheets.indices }
                    .forEach { neighbour -> render(document, plan, neighbour) }
            }
                .onSuccess { _state.update { it.copy(rendering = false) } }
                .onFailure { failure ->
                    // Cancellation is normal here: it means newer settings arrived.
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    _state.update {
                        it.copy(
                            rendering = false,
                            error = failure.message ?: "Could not draw the preview.",
                        )
                    }
                }
        }
    }

    /** Imposes and renders one sheet, then publishes it. */
    private suspend fun render(
        document: LoadedDocument,
        plan: ImpositionPlan,
        index: Int,
    ) {
        if (_state.value.previews.containsKey(index)) return

        // Only the visible sheet is imposed. Preview then costs the same whether
        // the document is four pages or four hundred, which is what keeps the
        // controls feeling live on a large file.
        val singleSheet = plan.copy(sheets = listOf(plan.sheets[index]))
        val target = File(context.cacheDir, "preview-$index.pdf")
        engine.impose(document.file, singleSheet, target)
        val bitmap = previewRenderer.renderPage(target, pageIndex = 0, targetWidthPx = PREVIEW_WIDTH_PX)

        _state.update { current ->
            current.copy(previews = (current.previews + (index to bitmap)).evictFarFrom(current.previewIndex))
        }
    }

    /**
     * Keeps only the sheet in view and its immediate neighbours.
     *
     * Each rendered sheet is several megabytes, so without this a long document
     * accumulates every sheet the user has swiped past until the process is
     * killed. Anything evicted is cheap to draw again.
     */
    private fun Map<Int, Bitmap>.evictFarFrom(index: Int): Map<Int, Bitmap> =
        filterKeys { kotlin.math.abs(it - index) <= CACHE_RADIUS }

    private companion object {
        const val DEBOUNCE_MS = 220L

        /**
         * Sized for zooming, not just for the thumbnail. At screen size this is
         * more pixels than a phone can show, but the preview exists so someone
         * can pinch in and judge whether 9-up will still be readable — and a
         * sheet rendered only for the thumbnail turns to mush the moment they do.
         *
         * Each sheet at this width costs roughly 12 MB, which is why the cache is
         * bounded rather than unlimited.
         */
        const val PREVIEW_WIDTH_PX = 1500

        /** Sheets either side of the visible one are kept; the rest are dropped. */
        const val CACHE_RADIUS = 1
    }
}
