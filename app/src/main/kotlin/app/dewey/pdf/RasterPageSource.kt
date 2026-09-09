package app.dewey.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import app.dewey.io.copyBounded
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException

/**
 * Opens an existing PDF for page-by-page rendering, the same way
 * app.dewey.index.OcrTextExtractor does and for the same two reasons: a
 * streaming content provider hands back a descriptor [PdfRenderer] cannot
 * seek, so a non-seekable open falls back to a bounded copy in the cache; and
 * a page rendered at print resolution costs tens of megabytes each, so pages
 * are rendered scaled to a target long edge rather than at their nominal
 * size. Shared here because both [RasterTools.pdfToImages] and
 * [RasterTools.compress] need to rasterize an arbitrary source PDF, and this
 * fallback is not something to get subtly wrong twice.
 */
internal class RasterPageSource(
    private val resolver: ContentResolver,
    private val cacheDir: File,
    private val maxSpoolBytes: Long,
) {

    /** A rendered page. [pageWidthPt]/[pageHeightPt] are the source page's own size, in points. */
    data class RenderedPage(val bitmap: Bitmap, val pageWidthPt: Float, val pageHeightPt: Float)

    private sealed interface Open<out T> {
        data class Success<T>(val value: T) : Open<T>
        data object NeedsCopy : Open<Nothing>
        data object Unreadable : Open<Nothing>
        data object Protected : Open<Nothing>
        data object TooLarge : Open<Nothing>
    }

    /**
     * Opens [uri] and hands the renderer to [action], falling back to a cache
     * copy if the provider's descriptor cannot be sought. The renderer (and
     * any spooled copy) is closed before this returns, whatever happens —
     * [action] must not let it escape.
     */
    suspend fun <T> withRenderer(
        uri: Uri,
        sizeBytes: Long,
        action: suspend (PdfRenderer) -> T,
    ): Result<T> {
        if (sizeBytes > maxSpoolBytes) {
            return Result.failure(RasterToolException(RasterFailure.TooLarge(uri.lastPathSegment.orEmpty())))
        }

        // openInPlace/openFromCopy classify the expected outcomes (protected,
        // too large, needs a copy); this is the safety net around whatever
        // they, or a caller's action, throw that isn't one of those — a
        // provider quirk, or the page-by-page work running out of memory.
        // Cancellation is the caller changing its mind, not a failure of this
        // document, so it is rethrown rather than reported as Unreadable.
        return try {
            val inPlace = openInPlace(uri, action)
            val outcome = if (inPlace is Open.NeedsCopy) openFromCopy(uri, action) else inPlace

            when (outcome) {
                is Open.Success -> Result.success(outcome.value)
                Open.Protected -> Result.failure(RasterToolException(RasterFailure.Protected(uri.lastPathSegment.orEmpty())))
                Open.TooLarge -> Result.failure(RasterToolException(RasterFailure.TooLarge(uri.lastPathSegment.orEmpty())))
                Open.Unreadable, Open.NeedsCopy ->
                    Result.failure(RasterToolException(RasterFailure.Unreadable(uri.lastPathSegment.orEmpty())))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not render $uri", e)
            Result.failure(RasterToolException(RasterFailure.Unreadable(uri.lastPathSegment.orEmpty())))
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Ran out of memory rendering $uri")
            Result.failure(RasterToolException(RasterFailure.Unreadable(uri.lastPathSegment.orEmpty())))
        }
    }

    private suspend fun <T> openInPlace(uri: Uri, action: suspend (PdfRenderer) -> T): Open<T> {
        val descriptor = resolver.openFileDescriptor(uri, "r") ?: return Open.Unreadable
        descriptor.use {
            val renderer = try {
                PdfRenderer(it)
            } catch (e: IOException) {
                Log.i(TAG, "Cannot render $uri in place (${e.message}); will copy it")
                return Open.NeedsCopy
            } catch (e: SecurityException) {
                return Open.Protected
            }
            return renderer.use { open -> Open.Success(action(open)) }
        }
    }

    private suspend fun <T> openFromCopy(uri: Uri, action: suspend (PdfRenderer) -> T): Open<T> {
        val spool = File.createTempFile("raster", ".pdf", cacheDir)
        try {
            val copied = resolver.openInputStream(uri)?.use { input ->
                spool.outputStream().use { output ->
                    copyBounded(input, output, maxSpoolBytes)
                }
            } ?: return Open.Unreadable

            if (!copied) return Open.TooLarge

            ParcelFileDescriptor.open(spool, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                return try {
                    PdfRenderer(descriptor).use { renderer -> Open.Success(action(renderer)) }
                } catch (e: SecurityException) {
                    Open.Protected
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not render the cached copy of $uri", e)
            return Open.Unreadable
        } finally {
            // Not deleteOnExit: a phone process is killed, it does not exit,
            // and a cache full of abandoned PDF copies is the user's storage.
            if (!spool.delete()) Log.w(TAG, "Could not delete the raster copy at $spool")
        }
    }

    /**
     * Renders page [index], scaled so its long edge lands near [longEdgePx].
     *
     * Rendering at the page's nominal size (a handful of hundred pixels) is
     * too soft to be worth much as an image and worse as OCR input; rendering
     * at print resolution is tens of megabytes a page a 300-page statement
     * cannot afford. [RenderedPage.pageWidthPt]/[pageHeightPt] carry the
     * page's real size forward so a caller rebuilding a PDF from these
     * bitmaps (see [RasterTools.compress]) can give the new page the same
     * proportions as the old one without recomputing a fit.
     */
    fun renderPage(renderer: PdfRenderer, index: Int, longEdgePx: Int): RenderedPage? =
        try {
            renderer.openPage(index).use { page ->
                val scale = longEdgePx.toFloat() / maxOf(page.width, page.height)
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                    // PdfRenderer draws transparent where the page is blank; a
                    // blank page has to come out white, not see-through black
                    // once flattened into a JPEG.
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }

                RenderedPage(bitmap, page.width.toFloat(), page.height.toFloat())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not render page $index", e)
            null
        }

    private companion object {
        const val TAG = "RasterPageSource"
    }
}
