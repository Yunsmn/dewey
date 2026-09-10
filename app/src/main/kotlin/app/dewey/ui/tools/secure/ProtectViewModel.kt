package app.dewey.ui.tools.secure

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.pdf.PdfToolkit
import app.dewey.pdf.protect
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.derivedFileName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the protect screen has chosen so far, and how its run is going. */
data class ProtectUiState(
    val source: PickedFile? = null,
    val password: String = "",
    val confirmPassword: String = "",
    val passwordVisible: Boolean = false,
    val allowPrinting: Boolean = true,
    val run: ToolRunState = ToolRunState.Idle,
) {
    val canRun: Boolean get() = canRunProtect(source != null, password, confirmPassword) && run !is ToolRunState.Running
    val suggestedFileName: String get() = derivedFileName(source?.name.orEmpty(), "protected")
}

/**
 * Encrypts a chosen PDF with a password the user picks — see [app.dewey.pdf.protect]
 * for what the password does and does not guarantee.
 *
 * A forgotten password cannot be recovered: there is no back door here, on
 * purpose, so the field the screen shows for it is the only copy that ever
 * exists. [afterRun] clears both fields the moment they have done their job,
 * so a screenshot or a leaked memory dump of a finished run has nothing left
 * to find.
 */
class ProtectViewModel(private val toolkit: PdfToolkit) : ViewModel() {

    private val _state = MutableStateFlow(ProtectUiState())
    val state: StateFlow<ProtectUiState> = _state.asStateFlow()

    fun onSourcePicked(file: PickedFile) {
        _state.value = _state.value.copy(source = file, run = ToolRunState.Idle)
    }

    fun onPasswordChanged(text: String) {
        _state.value = _state.value.copy(password = text)
    }

    fun onConfirmPasswordChanged(text: String) {
        _state.value = _state.value.copy(confirmPassword = text)
    }

    fun onPasswordVisibilityToggled() {
        _state.value = _state.value.copy(passwordVisible = !_state.value.passwordVisible)
    }

    fun onAllowPrintingChosen(allowPrinting: Boolean) {
        _state.value = _state.value.copy(allowPrinting = allowPrinting)
    }

    /** Called once the user has picked where to save — see [ProtectUiState.suggestedFileName]. */
    fun onDestinationChosen(target: Uri) {
        val current = _state.value
        val source = current.source ?: return
        if (!current.canRun) return

        _state.value = current.copy(run = ToolRunState.Running)
        viewModelScope.launch {
            val result = protect(
                workspace = toolkit.workspace,
                resolver = toolkit.resolver,
                source = source.uri,
                target = target,
                sizeBytes = source.sizeBytes,
                password = current.password,
                allowPrinting = current.allowPrinting,
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
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ProtectViewModel(toolkit) as T
        }
    }
}
