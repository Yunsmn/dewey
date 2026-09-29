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
import app.dewey.ui.tools.thumbnails.moveItem
import app.dewey.ui.tools.toolFailureMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReorderUiState(
    val file: PickedFile? = null,
    val pageCount: Int? = null,
    /** The whole page order so far, 0-based original indices — null until [pageCount] is known. */
    val order: List<Int>? = null,
    val runState: ToolRunState = ToolRunState.Idle,
) {
    val canRun: Boolean get() = canRunReorder(file, order, pageCount) && runState !is ToolRunState.Running
}

/** Drags pages to a new order, any number of moves before saving them all at once. */
class ReorderToolViewModel(private val toolkit: PdfToolkit) : ViewModel() {

    private val _state = MutableStateFlow(ReorderUiState())
    val state: StateFlow<ReorderUiState> = _state.asStateFlow()

    /** Owned here rather than by the screen so it survives recomposition and is closed exactly once — see [onCleared]. */
    val thumbnails = PageThumbnailSource(toolkit.resolver, toolkit.cacheDir)

    fun onFilePicked(file: PickedFile) {
        _state.value = ReorderUiState(file = file)
        loadPageCount(file)
    }

    /** [from] and [to] are positions in the current order, as [app.dewey.ui.tools.thumbnails.PageGrid]'s drag reports them. */
    fun onMove(from: Int, to: Int) {
        val order = _state.value.order ?: return
        _state.value = _state.value.copy(order = moveItem(order, from, to))
    }

    fun resetOrder() {
        val pageCount = _state.value.pageCount ?: return
        _state.value = _state.value.copy(order = identityOrder(pageCount))
    }

    fun reset() {
        _state.value = _state.value.copy(runState = ToolRunState.Idle)
    }

    fun run(target: Uri) {
        val current = _state.value
        val file = current.file
        val order = current.order
        if (file == null || order == null || !current.canRun) return
        _state.value = current.copy(runState = ToolRunState.Running)

        viewModelScope.launch {
            val result = toolkit.workspace.read(file.uri, file.sizeBytes) { document ->
                PageOperations.reorderTo(document, order).fold(
                    onSuccess = { saveOpenDocument(document, toolkit.resolver, target) },
                    onFailure = { Result.failure(it) },
                )
            }.flatten().onFailure { toolkit.discardOutput(target) }.onSuccess { toolkit.recordOutput(target) }

            _state.value = _state.value.copy(
                runState = result.fold(
                    onSuccess = { ToolRunState.Done(reorderSummary(), target) },
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
                onSuccess = { count -> _state.value.copy(pageCount = count, order = identityOrder(count)) },
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
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ReorderToolViewModel(toolkit) as T
        }
    }
}
