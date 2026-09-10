package app.dewey.ui.tools.secure

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.pdf.PdfToolkit
import app.dewey.pdf.watermark
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.derivedFileName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How see-through the stamped text is. Named for the reader, not the alpha value behind it. */
enum class WatermarkOpacity(val value: Float, val label: String) {
    LIGHT(0.10f, "Light"),
    MEDIUM(0.15f, "Medium"),
    BOLD(0.25f, "Bold"),
}

/** The two placements a watermark is actually asked for; anything else is a slider nobody needed. */
enum class WatermarkAngle(val degrees: Float, val label: String) {
    DIAGONAL(45f, "Diagonal"),
    HORIZONTAL(0f, "Horizontal"),
}

/** What the watermark screen has chosen so far, and how its run is going. */
data class WatermarkUiState(
    val source: PickedFile? = null,
    val text: String = "",
    val opacity: WatermarkOpacity = WatermarkOpacity.MEDIUM,
    val angle: WatermarkAngle = WatermarkAngle.DIAGONAL,
    val run: ToolRunState = ToolRunState.Idle,
) {
    val canRun: Boolean get() = canRunWatermark(source != null, text) && run !is ToolRunState.Running
    val suggestedFileName: String get() = derivedFileName(source?.name.orEmpty(), "watermarked")
}

/** Stamps a line of text across every page of a chosen PDF — see [app.dewey.pdf.watermark]. */
class WatermarkViewModel(private val toolkit: PdfToolkit) : ViewModel() {

    private val _state = MutableStateFlow(WatermarkUiState())
    val state: StateFlow<WatermarkUiState> = _state.asStateFlow()

    fun onSourcePicked(file: PickedFile) {
        _state.value = _state.value.copy(source = file, run = ToolRunState.Idle)
    }

    fun onTextChanged(text: String) {
        _state.value = _state.value.copy(text = text)
    }

    fun onOpacityChosen(opacity: WatermarkOpacity) {
        _state.value = _state.value.copy(opacity = opacity)
    }

    fun onAngleChosen(angle: WatermarkAngle) {
        _state.value = _state.value.copy(angle = angle)
    }

    /** Called once the user has picked where to save — see [WatermarkUiState.suggestedFileName]. */
    fun onDestinationChosen(target: Uri) {
        val current = _state.value
        val source = current.source ?: return
        if (!current.canRun) return

        _state.value = current.copy(run = ToolRunState.Running)
        viewModelScope.launch {
            val result = watermark(
                workspace = toolkit.workspace,
                resolver = toolkit.resolver,
                source = source.uri,
                target = target,
                sizeBytes = source.sizeBytes,
                text = current.text.trim(),
                opacity = current.opacity.value,
                angleDegrees = current.angle.degrees,
            )
            _state.value = _state.value.afterRun(result, target)
        }
    }

    fun reset() {
        _state.value = _state.value.copy(run = ToolRunState.Idle)
    }

    companion object {
        fun factory(toolkit: PdfToolkit) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = WatermarkViewModel(toolkit) as T
        }
    }
}
