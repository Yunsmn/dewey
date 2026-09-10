package app.dewey.ui.scan

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import app.dewey.io.copyBounded
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Copies a finished scan's bytes into a durable location.
 *
 * Kept as its own interface, with [ContentResolverScanFileCopy] the only real
 * implementation, so [ScanViewModel] can be driven by a fake in a plain-JVM
 * test without a real [ContentResolver] — the same reasoning as [ScanEngine].
 */
interface ScanFileCopy {

    /**
     * Copies [source] into [target].
     *
     * Throws on failure. [source] points into ML Kit's own storage (see
     * [app.dewey.scan.DocumentScanner.Outcome.Scanned]) and is only readable
     * while its grant lasts, so a [SecurityException] or
     * [FileNotFoundException] here means the grant is gone — expected if the
     * user sat in the save dialog a while, or the process was killed and
     * restored, not a bug in this copy.
     */
    fun copy(source: Uri, target: Uri)
}

/**
 * The real [ScanFileCopy], moving bytes over [ContentResolver] streams.
 *
 * The target already exists by the time this runs — SAF's create-document
 * flow makes an empty document before handing back its Uri — so any failure
 * here, including the source itself being unreadable, leaves that empty or
 * partial file behind. This best-effort deletes it so a failed save doesn't
 * leave a phantom document the user never asked for.
 */
class ContentResolverScanFileCopy(private val resolver: ContentResolver) : ScanFileCopy {

    override fun copy(source: Uri, target: Uri) {
        try {
            val input = resolver.openInputStream(source)
                ?: throw FileNotFoundException("no input stream for $source")
            input.use { streamIn ->
                // Deliberately not FileNotFoundException, and anything the
                // provider throws opening the target is rewrapped. ScanViewModel
                // reads FileNotFoundException and SecurityException as "the
                // scan's grant expired" — true of the source, false of the
                // target — so a folder that refuses the write would otherwise
                // tell the user to scan again when the scan is fine and it is
                // the save location that failed.
                val output = try {
                    resolver.openOutputStream(target)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw IOException("could not open the save location", e)
                } ?: throw IOException("could not open the save location")
                output.use { streamOut ->
                    val complete = copyBounded(streamIn, streamOut, MAX_SCAN_BYTES)
                    if (!complete) throw IOException("the scan was larger than $MAX_SCAN_BYTES bytes")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cleanUpBrokenTarget(target)
            throw e
        }
    }

    /** Best-effort removal of the empty or partial file a failed copy left behind. */
    private fun cleanUpBrokenTarget(target: Uri) {
        try {
            DocumentsContract.deleteDocument(resolver, target)
        } catch (e: Exception) {
            Log.w(TAG, "Could not remove the incomplete save at $target", e)
        }
    }

    private companion object {
        const val TAG = "ScanFileCopy"

        /**
         * A generous ceiling, not a real expectation: [app.dewey.scan.DocumentScanner]
         * caps a scan at 30 pages, so this exists to fail loudly on something
         * pathological rather than to matter in ordinary use.
         */
        const val MAX_SCAN_BYTES = 500L * 1024 * 1024
    }
}
