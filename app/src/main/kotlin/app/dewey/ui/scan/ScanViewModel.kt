package app.dewey.ui.scan

import android.app.Activity
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.dewey.scan.DocumentScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State for the scan screen.
 *
 * The state machine is the point of this class — see [ScanUiState] for what
 * each step means and why. This class only ever holds the current step; the
 * actual scan runs inside ML Kit's own activity, entirely outside this
 * process's control between [startScan] handing back an [ScanUiState.ReadyToLaunch]
 * and the screen delivering a result to [onScanResult].
 */
class ScanViewModel(private val engine: ScanEngine) : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Ready)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    /**
     * Asks the engine to start a scan. Ignored if a scan is already being
     * prepared or is ready to launch, so a doubled tap on the capture
     * affordance can't fire two scanner activities.
     */
    fun startScan(activity: Activity) {
        val current = _state.value
        if (current is ScanUiState.Preparing || current is ScanUiState.ReadyToLaunch) return

        _state.value = ScanUiState.Preparing
        engine.startScan(
            activity = activity,
            onReady = { intentSender -> _state.value = ScanUiState.ReadyToLaunch(intentSender) },
            onUnavailable = { _state.value = ScanUiState.Unavailable },
        )
    }

    /**
     * Called once the screen has actually launched the activity-result
     * launcher with the pending [ScanUiState.ReadyToLaunch.intentSender], so
     * that intent sender is never launched a second time from a
     * recomposition.
     */
    fun onScanLaunched() {
        if (_state.value is ScanUiState.ReadyToLaunch) {
            _state.value = ScanUiState.Scanning
        }
    }

    /** The scanner activity has returned. Reads a PDF, or notes the user cancelled. */
    fun onScanResult(result: ActivityResult) {
        val pdfUri = try {
            engine.resultPdf(result)
        } catch (e: RuntimeException) {
            _state.value = ScanUiState.Failed(e.message ?: "Couldn't read the finished scan")
            return
        }
        _state.value = if (pdfUri != null) ScanUiState.Scanned(pdfUri) else ScanUiState.Cancelled
    }

    /**
     * Back to the start — from a cancelled, unavailable, failed or already
     * saved scan, so the screen can offer another attempt without being torn
     * down and recreated.
     */
    fun reset() {
        _state.value = ScanUiState.Ready
    }

    companion object {
        /**
         * Not wired to [app.dewey.di.AppContainer] — that container doesn't
         * expose a [DocumentScanner] yet, and this file is not the place to
         * add one. Whoever wires this screen into navigation constructs a
         * [DocumentScanner] (or a fake [ScanEngine] for previews/tests) and
         * passes it here.
         */
        fun factory(scanner: DocumentScanner) = factory(DocumentScannerEngine(scanner))

        fun factory(engine: ScanEngine) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ScanViewModel(engine) as T
        }
    }
}
