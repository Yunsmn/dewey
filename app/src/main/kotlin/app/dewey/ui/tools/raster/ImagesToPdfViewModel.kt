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

/** What the images-to-PDF screen has chosen so far, and how its run is going. */
data class ImagesToPdfUiState(
    val images: List<PickedFile> = emptyList(),
    val quality: RasterQuality = RasterQuality.BALANCED,
    val run: ToolRunState = ToolRunState.Idle,
) {
    val canRun: Boolean get() = canRunImagesToPdf(images.size)

    /** Derived from the first image chosen — there is no single "source" name once several are combined. */
    val suggestedFileName: String get() = derivedFileName(images.firstOrNull()?.name.orEmpty(), "combined")
}

/**
 * Assembles the images the user picked, in the order they arranged them, into
 * one new PDF.
 *
 * The order in [ImagesToPdfUiState.images] is exactly the page order handed to
 * [app.dewey.pdf.RasterTools.imagesToPdf] — reordering here is reordering the
 * document, not just the picker's list.
 */
class ImagesToPdfViewModel(private val toolkit: PdfToolkit) : ViewModel() {

    private val _state = MutableStateFlow(ImagesToPdfUiState())
    val state: StateFlow<ImagesToPdfUiState> = _state.asStateFlow()

    /** Appended, not replaced — picking again is "add more", not "start over". */
    fun onImagesPicked(files: List<PickedFile>) {
        _state.value = _state.value.copy(images = _state.value.images + files, run = ToolRunState.Idle)
    }

    fun onQualityChosen(quality: RasterQuality) {
        _state.value = _state.value.copy(quality = quality)
    }

    fun onMove(from: Int, to: Int) {
        _state.value = _state.value.copy(images = reorderImage(_state.value.images, from, to))
    }

    fun onRemove(index: Int) {
        _state.value = _state.value.copy(images = removeImage(_state.value.images, index))
    }

    /** Called once the user has picked where to save — see [ImagesToPdfUiState.suggestedFileName]. */
    fun onDestinationChosen(target: Uri) {
        val current = _state.value
        if (current.images.isEmpty() || current.run is ToolRunState.Running) return

        _state.value = current.copy(run = ToolRunState.Running)
        viewModelScope.launch {
            val outcome = toolkit.raster.imagesToPdf(
                imageUris = current.images.map { it.uri },
                targetUri = target,
                quality = current.quality,
            )

            _state.value = _state.value.copy(
                run = outcome.fold(
                    onSuccess = { placed ->
                        ToolRunState.Done(imagesToPdfSummary(placed, current.images.size), target)
                    },
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
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ImagesToPdfViewModel(toolkit) as T
        }
    }
}
