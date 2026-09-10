package app.dewey.ui.tools.raster

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.pdf.PdfToolkit
import app.dewey.pdf.RasterQuality
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.derivedFileName
import app.dewey.ui.tools.toolFailureMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the compress screen has chosen so far, and how its run is going. */
data class CompressUiState(
    val source: PickedFile? = null,
    // SMALL, not BALANCED: someone reaching for a compress tool specifically
    // wants the smaller file, not the general-purpose default.
    val quality: RasterQuality = RasterQuality.SMALL,
    val run: ToolRunState = ToolRunState.Idle,
) {
    val canRun: Boolean get() = canRunCompress(source != null)
    val suggestedFileName: String get() = derivedFileName(source?.name.orEmpty(), "compressed")
}

/**
 * Rewrites a chosen PDF as a smaller one, at a location the user picks — see
 * [app.dewey.pdf.RasterTools.compress] for why this can come back larger
 * than the source instead of smaller, and [compressionSummary] for how that
 * is said rather than papered over.
 */
class CompressViewModel(private val toolkit: PdfToolkit) : ViewModel() {

    private val _state = MutableStateFlow(CompressUiState())
    val state: StateFlow<CompressUiState> = _state.asStateFlow()

    fun onSourcePicked(file: PickedFile) {
        _state.value = _state.value.copy(source = file, run = ToolRunState.Idle)
    }

    fun onQualityChosen(quality: RasterQuality) {
        _state.value = _state.value.copy(quality = quality)
    }

    /** Called once the user has picked where to save — see [CompressUiState.suggestedFileName]. */
    fun onDestinationChosen(target: Uri) {
        val current = _state.value
        val source = current.source ?: return
        if (current.run is ToolRunState.Running) return

        _state.value = current.copy(run = ToolRunState.Running)
        viewModelScope.launch {
            val outcome = toolkit.raster.compress(
                uri = source.uri,
                sizeBytes = source.sizeBytes,
                targetUri = target,
                quality = current.quality,
            ).onFailure { toolkit.discardOutput(target) }

            _state.value = _state.value.copy(
                run = outcome.fold(
                    onSuccess = { result -> ToolRunState.Done(compressionSummary(result), target) },
                    onFailure = { error -> ToolRunState.Failed(toolFailureMessage(error)) },
                ),
            )
        }
    }

    fun reset() {
        _state.value = _state.value.copy(run = ToolRunState.Idle)
    }

    companion object {
        fun factory(toolkit: PdfToolkit) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CompressViewModel(toolkit) as T
        }
    }
}
