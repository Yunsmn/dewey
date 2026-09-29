package app.dewey.ui.tools.split

import app.dewey.ui.tools.readPageCount
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.pdf.PdfToolkit
import app.dewey.pdf.SplitOperations
import app.dewey.pdf.split
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.derivedFileName
import app.dewey.ui.tools.describe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The two ways this app lets someone divide a PDF up; each owns its own field below. */
enum class SplitMode(val label: String) {
    EVERY_N_PAGES("Every N pages"),
    BY_RANGES("By ranges"),
}

/** What the split screen has chosen so far, and how its run is going. */
data class SplitUiState(
    val source: PickedFile? = null,
    val pageCount: Int? = null,
    val mode: SplitMode = SplitMode.EVERY_N_PAGES,
    val everyNText: String = "1",
    val rangesText: String = "",
    val destination: Uri? = null,
    val destinationName: String? = null,
    val run: ToolRunState = ToolRunState.Idle,
) {
    /** Null for blank or non-numeric text, rather than 0 — a typo should not read as "one page per file". */
    val everyN: Int? get() = everyNText.trim().toIntOrNull()

    val canRun: Boolean
        get() = canRunSplit(source != null, pageCount, destination != null, mode, everyN, rangesText) &&
            run !is ToolRunState.Running
}

/**
 * Divides one PDF into several new ones, saved into a folder the user
 * picks — see [app.dewey.pdf.split] for how each part is built and written
 * one at a time, and [SplitOperations] for how a mode's typed-in value
 * becomes the page groups that feeds.
 */
class SplitToolViewModel(private val toolkit: PdfToolkit) : ViewModel() {

    private val _state = MutableStateFlow(SplitUiState())
    val state: StateFlow<SplitUiState> = _state.asStateFlow()

    fun onSourcePicked(file: PickedFile) {
        _state.value = SplitUiState(source = file)
        loadPageCount(file)
    }

    fun onModeChosen(mode: SplitMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun onEveryNChanged(text: String) {
        _state.value = _state.value.copy(everyNText = text)
    }

    fun onRangesChanged(text: String) {
        _state.value = _state.value.copy(rangesText = text)
    }

    fun onDestinationPicked(tree: Uri) {
        val name = toolkit.resolver.describe(tree).name.ifEmpty { "Chosen folder" }
        _state.value = _state.value.copy(destination = tree, destinationName = name)
    }

    fun run() {
        val current = _state.value
        val source = current.source ?: return
        val pageCount = current.pageCount ?: return
        val destination = current.destination ?: return
        if (!current.canRun) return

        _state.value = current.copy(run = ToolRunState.Running)
        viewModelScope.launch {
            val groups = when (current.mode) {
                SplitMode.EVERY_N_PAGES -> SplitOperations.everyNPages(pageCount, current.everyN ?: 1)
                SplitMode.BY_RANGES -> SplitOperations.byRanges(current.rangesText, pageCount)
            }

            val outcome = groups.fold(
                onSuccess = { pageGroups ->
                    split(
                        workspace = toolkit.workspace,
                        resolver = toolkit.resolver,
                        source = source.uri,
                        sizeBytes = source.sizeBytes,
                        destinationTree = destination,
                        groups = pageGroups,
                        nameForPart = { partNumber -> derivedFileName(source.name, "part-$partNumber") },
                    )
                },
                onFailure = { Result.failure(it) },
            )

            _state.value = _state.value.afterRun(outcome)
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
                onFailure = { error -> _state.value.copy(run = ToolRunState.Failed(splitFailureMessage(error))) },
            )
        }
    }

    companion object {
        fun factory(toolkit: PdfToolkit) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SplitToolViewModel(toolkit) as T
        }
    }
}
