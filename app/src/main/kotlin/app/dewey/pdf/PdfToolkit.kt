package app.dewey.pdf

import android.content.ContentResolver
import android.net.Uri
import app.dewey.io.deleteDocumentQuietly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Everything a PDF tool screen needs, in one handle.
 *
 * The tools are split across files by what they do — pages, raster, marking,
 * protection — and between them they need a resolver, a cache directory, a
 * workspace, and the raster engine. Handing a screen this rather than the
 * whole application container keeps each screen's dependencies visible in its
 * signature, and lets a preview or a test construct one without the database,
 * the encoder or WorkManager coming along.
 */
class PdfToolkit(
    val resolver: ContentResolver,
    val cacheDir: File,
    val workspace: PdfWorkspace,
    val raster: RasterTools,
) {

    /**
     * Removes the file a failed run was meant to fill.
     *
     * The save dialog creates [target] before the tool starts, so without this
     * a wrong password or an unreadable source leaves an empty PDF in the
     * user's folder under the name they picked — found on the emulator, where
     * a failed unlock left a 0-byte "-unlocked.pdf" behind.
     */
    suspend fun discardOutput(target: Uri) = withContext(Dispatchers.IO) {
        resolver.deleteDocumentQuietly(target)
    }
}
