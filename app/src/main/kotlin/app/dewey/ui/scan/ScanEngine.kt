package app.dewey.ui.scan

import android.app.Activity
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

    /** The PDF a completed scan produced, or null if the user backed out. */
    fun resultPdf(result: ActivityResult): Uri?
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
            .addOnFailureListener { onUnavailable() }
    }

    override fun resultPdf(result: ActivityResult): Uri? = scanner.resultPdf(result)
}
