package io.github.abhishekcs194.printdeck.pdf.engine

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import io.github.abhishekcs194.printdeck.core.model.SizePt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads the facts about a document that imposition needs: how many pages, and
 * how big each one is.
 */
class PdfDocumentReader(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    data class DocumentInfo(
        val pageCount: Int,
        val pageSizes: List<SizePt>,
        val encrypted: Boolean = false,
    )

    suspend fun read(file: File): DocumentInfo = withContext(dispatcher) {
        PdfBoxRuntime.ensureInitialised(context)
        PDDocument.load(file).use { document ->
            DocumentInfo(
                pageCount = document.numberOfPages,
                pageSizes = document.pages.map { effectiveSize(it) },
                encrypted = document.isEncrypted,
            )
        }
    }

    companion object {
        /**
         * A page's size **as it is meant to be seen**.
         *
         * A page carries a media box and, separately, a rotation. A portrait box
         * with /Rotate 90 displays as landscape, and imposition works in display
         * space — so the rotation has to be folded in here. Reading the media box
         * alone silently lays out scanned documents sideways, which is the sort of
         * bug that only shows up on paper.
         */
        fun effectiveSize(page: PDPage): SizePt {
            val box = page.mediaBox
            val size = SizePt(box.width.toDouble(), box.height.toDouble())
            val quarterTurns = ((page.rotation % FULL_TURN) + FULL_TURN) % FULL_TURN
            return if (quarterTurns == QUARTER || quarterTurns == THREE_QUARTERS) size.swapped() else size
        }

        private const val FULL_TURN = 360
        private const val QUARTER = 90
        private const val THREE_QUARTERS = 270
    }
}
