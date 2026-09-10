package app.dewey.ui.scan

import android.content.IntentSender
import android.net.Uri

/**
 * Everything the scan screen can be showing.
 *
 * ML Kit's document scanner (see [app.dewey.scan.DocumentScanner]) runs in its
 * own activity, so this state machine only covers the before and after of that
 * activity — not a viewfinder this screen never draws.
 */
sealed interface ScanUiState {

    /** Nothing has happened yet, or a previous attempt was reset. */
    data object Ready : ScanUiState

    /**
     * Waiting on the scanner client to hand back an [IntentSender].
     *
     * On a device that has never used the scanner before, Play Services can
     * download the scanner module here, which is why this step can take
     * noticeably longer than a plain network round trip — see
     * [app.dewey.ui.scan.ScanViewModel].
     */
    data object Preparing : ScanUiState

    /**
     * The scanner is ready to launch. The screen is expected to call the
     * activity-result launcher with [intentSender] exactly once and then
     * report back via `onScanLaunched()`, so a recomposition never launches
     * the same intent twice.
     */
    data class ReadyToLaunch(val intentSender: IntentSender) : ScanUiState

    /** The scanner's own activity is on screen; this screen has nothing to show. */
    data object Scanning : ScanUiState

    /**
     * A scan completed. [pdfUri] points into ML Kit's own storage and is
     * readable only while the grant from that activity result lasts — it must
     * be saved (copied) somewhere durable, not stored as-is. See
     * [app.dewey.ui.scan.ScanViewModel.save].
     */
    data class Scanned(val pdfUri: Uri) : ScanUiState

    /**
     * The scan from [Scanned] is being copied into the location the user
     * chose via a SAF create-document flow. See
     * [app.dewey.ui.scan.ScanViewModel.save].
     */
    data object Saving : ScanUiState

    /** The scan was copied into the user's chosen location. */
    data object Saved : ScanUiState

    /**
     * The copy in [Saving] failed. [message] is written for a person, not a
     * log — see [app.dewey.ui.scan.ScanViewModel.save] for what can cause
     * this, including the scan's source grant having expired.
     */
    data class SaveFailed(val message: String) : ScanUiState

    /** The user backed out of the scanner without finishing. */
    data object Cancelled : ScanUiState

    /**
     * Play Services or the scanner module isn't available on this device.
     * Normal on some devices and on emulators without Play Services — not an
     * error, and it shouldn't look like one.
     */
    data object Unavailable : ScanUiState

    /** Something unexpected went wrong reading the finished scan. */
    data class Failed(val message: String) : ScanUiState
}
