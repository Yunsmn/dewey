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

data class ExtractUiState(
    val file: PickedFile? = null,
    val pageCount: Int? = null,
    val rangeText: String = "",
    val runState: ToolRunState = ToolRunState.Idle,
) {
    val canRun: Boolean get() = canRunExtract(file, pageCount, rangeText) && runState !is ToolRunState.Running
}

/** Pulls the pages named by a typed range out of one PDF into a new document. */
class ExtractToolViewModel(private val toolkit: PdfToolkit) : ViewModel() {

    private val _state = MutableStateFlow(ExtractUiState())
    val state: StateFlow<ExtractUiState> = _state.asStateFlow()

    fun onFilePicked(file: PickedFile) {
        _state.value = ExtractUiState(file = file)
        loadPageCount(file)
    }

    fun onRangeChanged(text: String) {
        _state.value = _state.value.copy(rangeText = text)
    }

    fun reset() {
        _state.value = _state.value.copy(runState = ToolRunState.Idle)
    }

    fun run(target: Uri) {
        val current = _state.value
        val file = current.file
        if (file == null || !canRunExtract(file, current.pageCount, current.rangeText)) return
        _state.value = current.copy(runState = ToolRunState.Running)

        viewModelScope.launch {
            // The copy is built and saved inside the same read block: source
            // pages are only valid while the source document is open.
            val result = toolkit.workspace.read(file.uri, file.sizeBytes) { source ->
                toolkit.workspace.newDocument().use { extracted ->
                    PageOperations.extract(source, current.rangeText, extracted).fold(
                        onSuccess = {
                            saveOpenDocument(extracted, toolkit.resolver, target).map { extracted.numberOfPages }
                        },
                        onFailure = { Result.failure(it) },
                    )
                }
            }.flatten().onFailure { toolkit.discardOutput(target) }

            _state.value = _state.value.copy(
                runState = result.fold(
                    onSuccess = { count -> ToolRunState.Done(extractSummary(count), target) },
                    onFailure = { ToolRunState.Failed(toolFailureMessage(it)) },
                ),
            )
        }
    }

    /**
     * Only applies the count if [file] is still the chosen one: a fast
     * second pick shouldn't have an earlier read's result land after it and
     * overwrite the newer file's still-loading state.
     */
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
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ExtractToolViewModel(toolkit) as T
        }
    }
}
