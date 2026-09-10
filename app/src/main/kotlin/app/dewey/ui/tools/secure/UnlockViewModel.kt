package app.dewey.ui.tools.secure

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.pdf.PdfToolkit
import app.dewey.pdf.unlock
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.derivedFileName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the unlock screen has chosen so far, and how its run is going. */
data class UnlockUiState(
    val source: PickedFile? = null,
    val password: String = "",
    val passwordVisible: Boolean = false,
    val run: ToolRunState = ToolRunState.Idle,
) {
    val canRun: Boolean get() = canRunUnlock(source != null, password) && run !is ToolRunState.Running
    val suggestedFileName: String get() = derivedFileName(source?.name.orEmpty(), "unlocked")
}

/**
 * Opens a chosen PDF with a typed password and writes a plain copy — see
 * [app.dewey.pdf.unlock] for why this is a straightforward reversal rather
 * than a recovery tool: a wrong password fails cleanly, on purpose.
 */
class UnlockViewModel(private val toolkit: PdfToolkit) : ViewModel() {

    private val _state = MutableStateFlow(UnlockUiState())
    val state: StateFlow<UnlockUiState> = _state.asStateFlow()

    fun onSourcePicked(file: PickedFile) {
        _state.value = _state.value.copy(source = file, run = ToolRunState.Idle)
    }

    fun onPasswordChanged(text: String) {
        _state.value = _state.value.copy(password = text)
    }

    fun onPasswordVisibilityToggled() {
        _state.value = _state.value.copy(passwordVisible = !_state.value.passwordVisible)
    }

    /** Called once the user has picked where to save — see [UnlockUiState.suggestedFileName]. */
    fun onDestinationChosen(target: Uri) {
        val current = _state.value
        val source = current.source ?: return
        if (!current.canRun) return

        _state.value = current.copy(run = ToolRunState.Running)
        viewModelScope.launch {
            val result = unlock(
                resolver = toolkit.resolver,
                cacheDir = toolkit.cacheDir,
                source = source.uri,
                target = target,
                sizeBytes = source.sizeBytes,
                password = current.password,
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
            override fun <T : ViewModel> create(modelClass: Class<T>): T = UnlockViewModel(toolkit) as T
        }
    }
}
