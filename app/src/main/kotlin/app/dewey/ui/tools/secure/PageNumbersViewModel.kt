package app.dewey.ui.tools.secure

import app.dewey.ui.tools.readPageCount
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.pdf.Corner
import app.dewey.pdf.PdfToolkit
import app.dewey.pdf.pageNumbers
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.derivedFileName
import app.dewey.ui.tools.toolFailureMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the page-numbers screen has chosen so far, and how its run is going. */
data class PageNumbersUiState(
    val source: PickedFile? = null,
    val pageCount: Int? = null,
    val corner: Corner = Corner.BOTTOM_CENTER,
    val startingNumberText: String = "1",
    val showTotal: Boolean = false,
    val run: ToolRunState = ToolRunState.Idle,
) {
    /** Null for blank or non-numeric text, rather than 0 — a typo should not read as "start at zero". */
    val startingNumber: Int? get() = startingNumberText.trim().toIntOrNull()

    val canRun: Boolean get() = canRunPageNumbers(source != null, startingNumber) && run !is ToolRunState.Running
    val suggestedFileName: String get() = derivedFileName(source?.name.orEmpty(), "numbered")
}

/** Stamps a running page number onto every page of a chosen PDF — see [app.dewey.pdf.pageNumbers]. */
class PageNumbersViewModel(private val toolkit: PdfToolkit) : ViewModel() {

    private val _state = MutableStateFlow(PageNumbersUiState())
    val state: StateFlow<PageNumbersUiState> = _state.asStateFlow()

    fun onSourcePicked(file: PickedFile) {
        _state.value = _state.value.copy(source = file, pageCount = null, run = ToolRunState.Idle)
        loadPageCount(file)
    }

    fun onCornerChosen(corner: Corner) {
        _state.value = _state.value.copy(corner = corner)
    }

    fun onStartingNumberChanged(text: String) {
        _state.value = _state.value.copy(startingNumberText = text)
    }

    fun onShowTotalChosen(showTotal: Boolean) {
        _state.value = _state.value.copy(showTotal = showTotal)
    }

    /** Called once the user has picked where to save — see [PageNumbersUiState.suggestedFileName]. */
    fun onDestinationChosen(target: Uri) {
        val current = _state.value
        val source = current.source ?: return
        val startingNumber = current.startingNumber ?: return
        if (!current.canRun) return

        _state.value = current.copy(run = ToolRunState.Running)
        viewModelScope.launch {
            val result = pageNumbers(
                workspace = toolkit.workspace,
                resolver = toolkit.resolver,
                source = source.uri,
                target = target,
                sizeBytes = source.sizeBytes,
                corner = current.corner,
                startingNumber = startingNumber,
                showTotal = current.showTotal,
            ).onFailure { toolkit.discardOutput(target) }.onSuccess { toolkit.recordOutput(target) }
            _state.value = _state.value.afterRun(result, target)
        }
    }

    fun reset() {
        _state.value = _state.value.copy(run = ToolRunState.Idle)
    }

    /**
     * Only applies the count if [file] is still the chosen one: a fast
     * second pick shouldn't have an earlier read's result land after it and
     * overwrite the newer file's still-loading state.
     */
    private fun loadPageCount(file: PickedFile) {
        viewModelScope.launch {
            val result = toolkit.readPageCount(file)
            if (_state.value.source?.uri != file.uri) return@launch
            _state.value = result.fold(
                onSuccess = { count -> _state.value.copy(pageCount = count) },
                onFailure = { error -> _state.value.copy(run = ToolRunState.Failed(toolFailureMessage(error))) },
            )
        }
    }

    companion object {
        fun factory(toolkit: PdfToolkit) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PageNumbersViewModel(toolkit) as T
        }
    }
}
