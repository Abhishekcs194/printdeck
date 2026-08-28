package io.github.abhishekcs194.printdeck.ui.documents

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.abhishekcs194.printdeck.data.DocumentRepository
import io.github.abhishekcs194.printdeck.data.LoadedDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val repository: DocumentRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val document: LoadedDocument? = null,
        val error: String? = null,
        /** Documents opened during this session. */
        val recents: List<LoadedDocument> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun openDocument(uri: Uri) = load { repository.open(uri) }

    fun openImages(uris: List<Uri>) = load { repository.openImages(uris) }

    private fun load(block: suspend () -> Result<LoadedDocument>) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            block()
                .onSuccess { document ->
                    _state.update {
                        it.copy(
                            loading = false,
                            document = document,
                            // Most recent first, and the same file opened twice
                            // does not appear twice.
                            recents = (listOf(document) + it.recents)
                                .distinctBy { entry -> entry.file.absolutePath }
                                .take(MAX_RECENTS),
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = failure.message ?: "That file could not be opened.",
                        )
                    }
                }
        }
    }

    /** Clears the one-shot navigation signal once the editor has been opened. */
    fun consumeOpenedDocument() = _state.update { it.copy(document = null) }

    fun dismissError() = _state.update { it.copy(error = null) }

    private companion object {
        const val MAX_RECENTS = 10
    }
}
