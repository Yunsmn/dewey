package app.dewey.scan

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResult
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Capture, edge detection, perspective correction and multi-page assembly.
 *
 * All of it is Play Services' scanner rather than a hand-rolled camera pipeline.
 * That is a deliberate trade: it runs in its own process, so the app needs no
 * CAMERA permission and cannot leak a camera session, and its corner detection
 * is better than anything worth writing here. The cost is a dependency on Play
 * Services. There is deliberately no isAvailable() to call first: the module
 * can be absent, present, or downloadable-on-demand, and the only way to learn
 * which is to ask for the scanner and see. So [intentSender] failing is the
 * absence signal, and callers must handle it — see app.dewey.ui.scan.ScanEngine,
 * which treats any failure there as "not available on this device".
 */
class DocumentScanner(private val context: Context) {

    /**
     * A PDF is requested directly, so a multi-page scan arrives as one document
     * rather than a pile of JPEGs the app would have to assemble itself.
     */
    private val options = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(MAX_PAGES)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .build()

    /**
     * Starts the scanner. The returned task resolves to an [IntentSender] the
     * caller launches; it fails when the Play Services module is unavailable,
     * which is a normal outcome on devices without it rather than a bug.
     */
    fun intentSender(activity: Activity): Task<IntentSender> =
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)

    /**
     * The PDF a completed scan produced, or null if the user backed out.
     *
     * The URI points into the scanner's own storage and is readable only while
     * the grant lasts, so callers must copy the bytes rather than store the URI.
     */
    fun resultPdf(result: ActivityResult): Uri? {
        if (result.resultCode != Activity.RESULT_OK) return null

        val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val uri = scan?.pdf?.uri
        if (uri == null) {
            Log.w(TAG, "Scan finished without a PDF; result code ${result.resultCode}")
        }
        return uri
    }

    private companion object {
        const val TAG = "DocumentScanner"

        /**
         * Play Services enforces a ceiling anyway, and an unbounded scan is a
         * memory problem the user discovers only after twenty minutes of work.
         */
        const val MAX_PAGES = 30
    }
}
