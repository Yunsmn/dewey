package app.dewey.index

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import app.dewey.io.copyBounded
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads a scanned PDF by rendering pages and running text recognition.
 *
 * This is the expensive path and only runs when [PdfTextExtractor] came back
 * empty. Two things keep it survivable on a phone: only the first [maxPages] are
 * read, and each page's bitmap is recycled before the next is allocated. A
 * three-hundred-page scan rendered eagerly is an out-of-memory crash, not a slow
 * operation.
 */
class OcrTextExtractor(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val maxPages: Int = MAX_PAGES,
    /**
     * Where a non-seekable document may be copied so it can be rendered. Null
     * disables that fallback, which only costs those documents their OCR.
     */
    private val cacheDir: File? = null,
    private val maxSpoolBytes: Long = MAX_SPOOL_BYTES,
) {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun extract(uri: Uri): String? = withContext(io) {
        try {
            val text = readInPlace(uri) ?: readFromCopy(uri)
            text?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            // Cancellation is the caller changing its mind, not a failure of
            // this document. Reporting it as "no text" would let the indexer
            // record an empty result and never try again.
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "OCR failed for $uri", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Ran out of memory during OCR of $uri")
            null
        }
    }

    /**
     * Renders straight off the provider's descriptor.
     *
     * Null means the descriptor could not be rendered at all, which is the
     * signal to try a copy. A document that rendered but held no recognisable
     * text comes back as an empty string, so it is not copied and re-read for
     * nothing.
     */
    private suspend fun readInPlace(uri: Uri): String? {
        val descriptor = resolver.openFileDescriptor(uri, "r") ?: return null
        descriptor.use {
            val renderer = try {
                PdfRenderer(it)
            } catch (e: IOException) {
                // PdfRenderer has to seek. A provider that streams its content —
                // a cloud one, typically, or anything backed by a pipe — hands
                // back a descriptor that cannot, and this is where that shows up.
                Log.i(TAG, "Cannot render $uri in place (${e.message}); will copy it")
                return null
            } catch (e: SecurityException) {
                // Password-protected. Copying it would not help.
                Log.i(TAG, "$uri is protected; skipping OCR")
                return ""
            }
            return renderer.use { open -> readPages(open) }
        }
    }

    /**
     * Copies the document into the cache and renders that instead.
     *
     * A local file can seek, which is the whole point. The copy is deleted
     * before this returns, whatever happens.
     */
    private suspend fun readFromCopy(uri: Uri): String? {
        val cache = cacheDir ?: return null
        val spool = File.createTempFile("ocr", ".pdf", cache)
        try {
            val copied = resolver.openInputStream(uri)?.use { input ->
                spool.outputStream().use { output -> copyBounded(input, output, maxSpoolBytes) }
            } ?: return null

            if (!copied) {
                Log.i(TAG, "$uri is larger than the ${maxSpoolBytes / 1_000_000}MB OCR copy limit")
                return null
            }

            ParcelFileDescriptor.open(spool, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer -> return readPages(renderer) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not render the cached copy of $uri", e)
            return null
        } finally {
            // Not deleteOnExit: a phone process is killed, it does not exit, and
            // a cache full of abandoned PDF copies is the user's storage.
            if (!spool.delete()) Log.w(TAG, "Could not delete the OCR copy at $spool")
        }
    }

    private suspend fun readPages(renderer: PdfRenderer): String {
        val pages = minOf(renderer.pageCount, maxPages)
        val builder = StringBuilder()

        for (index in 0 until pages) {
            coroutineContext.ensureActive()
            val bitmap = renderPage(renderer, index) ?: continue
            try {
                builder.append(recognise(bitmap)).append('\n')
            } finally {
                bitmap.recycle()
            }
        }
        return builder.toString()
    }

    private fun renderPage(renderer: PdfRenderer, index: Int): Bitmap? =
        try {
            renderer.openPage(index).use { page ->
                // Scale so the long edge lands near TARGET_LONG_EDGE. Rendering at
                // the page's nominal size gives text too small for recognition;
                // rendering at print resolution wastes tens of megabytes a page.
                val scale = TARGET_LONG_EDGE.toFloat() / maxOf(page.width, page.height)
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)

                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    // PdfRenderer draws transparent where the page is blank, and
                    // recognition on a transparent ground finds nothing.
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not render page $index", e)
            null
        }

    private suspend fun recognise(bitmap: Bitmap): String =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { continuation.resume(it.text) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

    companion object {
        private const val TAG = "OcrTextExtractor"
        private const val MAX_PAGES = 10
        private const val TARGET_LONG_EDGE = 2000

        /**
         * The copy fallback writes to the app's cache, so it is bounded. 40MB is
         * generous for a scan of the first few pages' worth of anything the
         * indexer will actually read, and small enough not to matter on a phone
         * whose storage is already tight.
         */
        const val MAX_SPOOL_BYTES = 40L * 1024 * 1024

    }
}
