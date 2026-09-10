package app.dewey.ui.scan

import android.app.Activity
import android.util.Log
import android.content.IntentSender
import android.net.Uri
import androidx.activity.result.ActivityResult
import app.dewey.scan.DocumentScanner

/**
 * What the scan screen needs from the scanning engine, with none of ML Kit's
 * or Play Services' own types leaking past the boundary.
 *
 * [DocumentScanner] hands back a `Task<IntentSender>` and reads an
 * [ActivityResult], both real Android/GMS types that only behave correctly on
 * a device or emulator. Behind this interface, [ScanViewModel] only ever sees
 * plain callbacks and a nullable [Uri], which is what lets it be driven by a
 * fake in a plain-JVM test instead of needing Robolectric or a real device.
 */
interface ScanEngine {

    /**
     * Starts a scan. Exactly one of [onReady] or [onUnavailable] is called,
     * asynchronously, once the scanner client resolves.
     *
     * [activity] is taken as a parameter rather than stored, because the
     * caller — [ScanViewModel] — is a `ViewModel` and must outlive any single
     * `Activity` instance across a rotation.
     */
    fun startScan(activity: Activity, onReady: (IntentSender) -> Unit, onUnavailable: () -> Unit)

    /** What the finished scan produced — see [DocumentScanner.Outcome]. */
    fun outcome(result: ActivityResult): DocumentScanner.Outcome
}

/**
 * The real [ScanEngine], adapting [DocumentScanner]'s GMS `Task` into plain
 * callbacks.
 *
 * [DocumentScanner.intentSender] is documented as failing exactly when the
 * Play Services scanner module is unavailable — see its class doc — so any
 * failure here is reported as [onUnavailable] rather than guessed at with
 * exception-type matching.
 */
class DocumentScannerEngine(private val scanner: DocumentScanner) : ScanEngine {

    override fun startScan(activity: Activity, onReady: (IntentSender) -> Unit, onUnavailable: () -> Unit) {
        scanner.intentSender(activity)
            .addOnSuccessListener { intentSender -> onReady(intentSender) }
            .addOnFailureListener { error ->
                // Logged before it is swallowed. The class doc says this call
                // fails exactly when the scanner module is unavailable, but
                // that has never been checked against a real device — and if
                // it is ever wrong (a network drop mid-download, a bad
                // options object), every such case is otherwise
                // indistinguishable from "this phone cannot scan" with
                // nothing in a bug report to tell them apart.
                Log.w(TAG, "Could not start the scanner", error)
                onUnavailable()
            }
    }

    override fun outcome(result: ActivityResult): DocumentScanner.Outcome =
        scanner.outcome(result)

    private companion object {
        const val TAG = "ScanEngine"
    }
}