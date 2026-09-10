package app.dewey.ui.scan

import android.app.Activity
import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.scan.DocumentScanner
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State for the scan screen.
 *
 * The state machine is the point of this class — see [ScanUiState] for what
 * each step means and why. This class only ever holds the current step; the
 * actual scan runs inside ML Kit's own activity, entirely outside this
 * process's control between [startScan] handing back an [ScanUiState.ReadyToLaunch]
 * and the screen delivering a result to [onScanResult]. [save] is the other
 * side of that same boundary: it runs entirely inside this process, but
 * across a SAF create-document round trip the screen drives.
 */
class ScanViewModel(
    private val engine: ScanEngine,
    private val copier: ScanFileCopy,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

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
        val outcome = try {
            engine.outcome(result)
        } catch (e: RuntimeException) {
            _state.value = ScanUiState.Failed(e.message ?: "Couldn't read the finished scan")
            return
        }

        // NoDocument used to arrive here as null, indistinguishable from a
        // cancellation, so a scan that genuinely failed told the user they had
        // backed out. It is a failure and it says so.
        _state.value = when (outcome) {
            is DocumentScanner.Outcome.Scanned -> ScanUiState.Scanned(outcome.pdf)
            is DocumentScanner.Outcome.Cancelled -> ScanUiState.Cancelled
            is DocumentScanner.Outcome.NoDocument ->
                ScanUiState.Failed("The scan finished but ${outcome.reason}.")
        }
    }

    /**
     * Copies the scan from [ScanUiState.Scanned] into [targetUri], which the
     * screen obtained from a SAF create-document flow.
     *
     * Ignored outside [ScanUiState.Scanned] — a save triggered by a stray
     * recomposition, a double tap, or a race with [reset] must not silently
     * copy into a document nobody is looking at, or overwrite one the user
     * has already moved past.
     *
     * The copy runs on [io] rather than blocking the caller, since it moves
     * the whole PDF through [copier]. [ScanFileCopy.copy] is documented to
     * throw [SecurityException] or [FileNotFoundException] specifically when
     * ML Kit's grant on the source has expired — a real outcome if the save
     * dialog sat open a while, or the process was killed and restored — so
     * that is reported as its own honest message rather than folded into a
     * generic failure.
     */
    fun save(targetUri: Uri) {
        val current = _state.value
        if (current !is ScanUiState.Scanned) return
        val pdfUri = current.pdfUri

        _state.value = ScanUiState.Saving
        viewModelScope.launch(io) {
            try {
                copier.copy(pdfUri, targetUri)
                _state.value = ScanUiState.Saved
            } catch (e: CancellationException) {
                // The caller changed its mind, not a failed save. Swallowing
                // this instead of rethrowing has been a real bug here twice.
                throw e
            } catch (e: SecurityException) {
                _state.value = ScanUiState.SaveFailed(EXPIRED_SCAN_MESSAGE)
            } catch (e: FileNotFoundException) {
                _state.value = ScanUiState.SaveFailed(EXPIRED_SCAN_MESSAGE)
            } catch (e: Exception) {
                Log.w(TAG, "Could not save scan $pdfUri to $targetUri", e)
                _state.value = ScanUiState.SaveFailed("Couldn't save the scan. Try again.")
            }
        }
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

        private const val TAG = "ScanViewModel"

        /**
         * What a person sees when [ScanFileCopy.copy] fails because the
         * source's grant is gone rather than because of some other write
         * failure — see [save]. Naming it once keeps the production and
         * SecurityException/FileNotFoundException branches from drifting
         * apart.
         */
        private const val EXPIRED_SCAN_MESSAGE = "The scan is no longer available — scan again."

        /** Wires the real scanner and a real, [ContentResolver]-backed copy. */
        fun factory(scanner: DocumentScanner, resolver: ContentResolver) =
            factory(DocumentScannerEngine(scanner), ContentResolverScanFileCopy(resolver))

        /**
         * Not wired to [app.dewey.di.AppContainer] — that container doesn't
         * expose a [DocumentScanner] yet, and this file is not the place to
         * add one. Whoever wires this screen into navigation constructs a
         * [DocumentScanner] (or a fake [ScanEngine] for previews/tests) and
         * a [ScanFileCopy], and passes them here.
         */
        fun factory(engine: ScanEngine, copier: ScanFileCopy) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ScanViewModel(engine, copier) as T
        }
    }
}
