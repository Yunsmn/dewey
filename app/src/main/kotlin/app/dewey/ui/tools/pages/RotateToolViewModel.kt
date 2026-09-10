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

data class RotateUiState(
    val file: PickedFile? = null,
    val pageCount: Int? = null,
    val rangeText: String = "",
    val degrees: Int? = null,
    val runState: ToolRunState = ToolRunState.Idle,
) {
    val canRun: Boolean get() = canRunRotate(file, pageCount, degrees) && runState !is ToolRunState.Running
}

/** Turns the pages named by a typed range — or every page, left blank — by a quarter-turn. */
class RotateToolViewModel(private val toolkit: PdfToolkit) : ViewModel() {

    private val _state = MutableStateFlow(RotateUiState())
    val state: StateFlow<RotateUiState> = _state.asStateFlow()

    fun onFilePicked(file: PickedFile) {
        _state.value = RotateUiState(file = file)
        loadPageCount(file)
    }

    fun onRangeChanged(text: String) {
        _state.value = _state.value.copy(rangeText = text)
    }

    fun onDegreesChosen(degrees: Int) {
        _state.value = _state.value.copy(degrees = degrees)
    }

    fun reset() {
        _state.value = _state.value.copy(runState = ToolRunState.Idle)
    }

    fun run(target: Uri) {
        val current = _state.value
        val file = current.file
        val pageCount = current.pageCount
        val degrees = current.degrees
        if (file == null || pageCount == null || degrees == null || !current.canRun) return

        val spec = effectiveRotateRange(current.rangeText, pageCount)
        // Computed ahead of the run rather than read back off the document
        // afterwards: rotation doesn't change how many pages there are, so
        // the document itself has no count of "how many were touched" to ask.
        val rotatedCount = PageOperations.parsePageRange(spec, pageCount).getOrNull()?.size ?: 0
        _state.value = current.copy(runState = ToolRunState.Running)

        viewModelScope.launch {
            // The source is opened once, mutated in place, and saved to the
            // new target before it closes — the file on disk is never
            // touched.
            val result = toolkit.workspace.read(file.uri, file.sizeBytes) { document ->
                PageOperations.rotate(document, spec, degrees).fold(
                    onSuccess = { saveOpenDocument(document, toolkit.resolver, target) },
                    onFailure = { Result.failure(it) },
                )
            }.flatten().onFailure { toolkit.discardOutput(target) }.onSuccess { toolkit.recordOutput(target) }

            _state.value = _state.value.copy(
                runState = result.fold(
                    onSuccess = { ToolRunState.Done(rotateSummary(rotatedCount, degrees), target) },
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
            override fun <T : ViewModel> create(modelClass: Class<T>): T = RotateToolViewModel(toolkit) as T
        }
    }
}
