package app.dewey.ui.tools.pages

import app.dewey.ui.tools.readPageCount
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

data class ReorderUiState(
    val file: PickedFile? = null,
    val pageCount: Int? = null,
    val fromText: String = "",
    val toText: String = "",
    val runState: ToolRunState = ToolRunState.Idle,
) {
    val canRun: Boolean get() = canRunReorder(file, pageCount, fromText, toText) && runState !is ToolRunState.Running
}

/** Moves one page to a new position, renumbering everything between as PDFBox does it. */
class ReorderToolViewModel(private val toolkit: PdfToolkit) : ViewModel() {

    private val _state = MutableStateFlow(ReorderUiState())
    val state: StateFlow<ReorderUiState> = _state.asStateFlow()

    fun onFilePicked(file: PickedFile) {
        _state.value = ReorderUiState(file = file)
        loadPageCount(file)
    }

    fun onFromChanged(text: String) {
        _state.value = _state.value.copy(fromText = text)
    }

    fun onToChanged(text: String) {
        _state.value = _state.value.copy(toText = text)
    }

    fun reset() {
        _state.value = _state.value.copy(runState = ToolRunState.Idle)
    }

    fun run(target: Uri) {
        val current = _state.value
        val file = current.file
        if (file == null || !current.canRun) return
        // Safe: canRun above already confirmed both parse as page numbers.
        val from = current.fromText.toInt()
        val to = current.toText.toInt()
        _state.value = current.copy(runState = ToolRunState.Running)

        viewModelScope.launch {
            val result = toolkit.workspace.read(file.uri, file.sizeBytes) { document ->
                PageOperations.reorder(document, from, to).fold(
                    onSuccess = { saveOpenDocument(document, toolkit.resolver, target) },
                    onFailure = { Result.failure(it) },
                )
            }.flatten()

            _state.value = _state.value.copy(
                runState = result.fold(
                    onSuccess = { ToolRunState.Done(reorderSummary(from, to), target) },
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

    companion object {
        fun factory(toolkit: PdfToolkit) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ReorderToolViewModel(toolkit) as T
        }
    }
}
