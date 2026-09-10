package app.dewey.ui.tools.pages

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.pdf.PageOperations
import app.dewey.pdf.PdfToolkit
import app.dewey.pdf.flatten
import app.dewey.pdf.saveOpenDocument
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.toolFailureMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MergeUiState(
    val files: List<PickedFile> = emptyList(),
    val runState: ToolRunState = ToolRunState.Idle,
) {
    val canRun: Boolean get() = canRunMerge(files) && runState !is ToolRunState.Running
}

/**
 * Merges several PDFs, in the order the user arranged them, into one new
 * file.
 *
 * State lives here rather than in composition state so a merge started just
 * before a rotation survives it instead of dying with the output half
 * written — see the class doc on [PdfToolkit] for why that matters for a
 * multi-document read this size.
 */
class MergeToolViewModel(private val toolkit: PdfToolkit) : ViewModel() {

    private val _state = MutableStateFlow(MergeUiState())
    val state: StateFlow<MergeUiState> = _state.asStateFlow()

    fun addFiles(newFiles: List<PickedFile>) {
        _state.value = _state.value.copy(files = _state.value.files + newFiles)
    }

    fun moveUp(index: Int) {
        _state.value = _state.value.copy(files = moveUp(_state.value.files, index))
    }

    fun moveDown(index: Int) {
        _state.value = _state.value.copy(files = moveDown(_state.value.files, index))
    }

    fun remove(index: Int) {
        _state.value = _state.value.copy(files = removeAt(_state.value.files, index))
    }

    /** Back to editable, keeping the chosen files — the common case is fixing the order, not re-picking. */
    fun reset() {
        _state.value = _state.value.copy(runState = ToolRunState.Idle)
    }

    fun run(target: Uri) {
        val files = _state.value.files
        if (!canRunMerge(files)) return
        _state.value = _state.value.copy(runState = ToolRunState.Running)

        viewModelScope.launch {
            val sources = files.map { it.uri to it.sizeBytes }
            // The save happens inside readAll's block, before any source
            // document closes: merged pages still reference their source's
            // resources (fonts, images), so saving after close would write a
            // broken file. See PdfWorkspace.readAll's doc.
            val result = toolkit.workspace.readAll(sources) { documents ->
                toolkit.workspace.newDocument().use { merged ->
                    PageOperations.merge(documents, merged).fold(
                        onSuccess = {
                            saveOpenDocument(merged, toolkit.resolver, target).map { merged.numberOfPages }
                        },
                        onFailure = { Result.failure(it) },
                    )
                }
            }.flatten()

            _state.value = _state.value.copy(
                runState = result.fold(
                    onSuccess = { pageCount -> ToolRunState.Done(mergeSummary(files.size, pageCount), target) },
                    onFailure = { ToolRunState.Failed(toolFailureMessage(it)) },
                ),
            )
        }
    }

    companion object {
        fun factory(toolkit: PdfToolkit) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MergeToolViewModel(toolkit) as T
        }
    }
}
