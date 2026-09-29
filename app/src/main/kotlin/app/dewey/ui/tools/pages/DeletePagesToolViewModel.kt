package app.dewey.ui.tools.pages

import app.dewey.ui.tools.readPageCount
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.pdf.PageOperations
import app.dewey.pdf.PageThumbnailSource
import app.dewey.pdf.PdfToolkit
import app.dewey.pdf.flatten
import app.dewey.pdf.saveOpenDocument
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.thumbnails.selectAll
import app.dewey.ui.tools.thumbnails.specFromSelection
import app.dewey.ui.tools.thumbnails.toggleSelection
import app.dewey.ui.tools.toolFailureMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeleteUiState(
    val file: PickedFile? = null,
    val pageCount: Int? = null,
    val selected: Set<Int> = emptySet(),
    val runState: ToolRunState = ToolRunState.Idle,
) {
    val canRun: Boolean get() = canRunDelete(file, pageCount, selected) && runState !is ToolRunState.Running
}

/** Removes the pages checked in select mode from one PDF. */
class DeletePagesToolViewModel(private val toolkit: PdfToolkit) : ViewModel() {

    private val _state = MutableStateFlow(DeleteUiState())
    val state: StateFlow<DeleteUiState> = _state.asStateFlow()

    /** Owned here rather than by the screen so it survives recomposition and is closed exactly once — see [onCleared]. */
    val thumbnails = PageThumbnailSource(toolkit.resolver, toolkit.cacheDir)

    fun onFilePicked(file: PickedFile) {
        _state.value = DeleteUiState(file = file)
        loadPageCount(file)
    }

    fun onToggle(pageIndex: Int) {
        _state.value = _state.value.copy(selected = toggleSelection(_state.value.selected, pageIndex))
    }

    fun onSelectAll() {
        val pageCount = _state.value.pageCount ?: return
        _state.value = _state.value.copy(selected = selectAll(pageCount))
    }

    fun onClearSelection() {
        _state.value = _state.value.copy(selected = emptySet())
    }

    fun reset() {
        _state.value = _state.value.copy(runState = ToolRunState.Idle)
    }

    fun run(target: Uri) {
        val current = _state.value
        val file = current.file
        val pageCount = current.pageCount
        if (file == null || pageCount == null || !current.canRun) return

        val deletedCount = current.selected.size
        val spec = specFromSelection(current.selected)
        _state.value = current.copy(runState = ToolRunState.Running)

        viewModelScope.launch {
            val result = toolkit.workspace.read(file.uri, file.sizeBytes) { document ->
                PageOperations.delete(document, spec).fold(
                    onSuccess = { saveOpenDocument(document, toolkit.resolver, target) },
                    onFailure = { Result.failure(it) },
                )
            }.flatten().onFailure { toolkit.discardOutput(target) }.onSuccess { toolkit.recordOutput(target) }

            _state.value = _state.value.copy(
                runState = result.fold(
                    onSuccess = {
                        ToolRunState.Done(deleteSummary(deletedCount, pageCount - deletedCount), target)
                    },
                    onFailure = { ToolRunState.Failed(toolFailureMessage(it)) },
                ),
            )
        }
    }

    /** See [ExtractToolViewModel.loadPageCount] — same guard against a stale second pick. */
    private fun loadPageCount(file: PickedFile) {
        viewModelScope.launch {
            val result = toolkit.readPageCount(file)
            if (_state.value.file?.uri != file.uri) return@launch
            _state.value = result.fold(
                onSuccess = { count -> _state.value.copy(pageCount = count) },
                onFailure = { error -> _state.value.copy(runState = ToolRunState.Failed(toolFailureMessage(error))) },
            )
        }
    }

    override fun onCleared() {
        thumbnails.close()
    }

    companion object {
        fun factory(toolkit: PdfToolkit) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DeletePagesToolViewModel(toolkit) as T
        }
    }
}
