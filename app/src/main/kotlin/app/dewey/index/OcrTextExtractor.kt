package app.dewey.index

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
) {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun extract(uri: Uri): String? = withContext(io) {
        try {
            resolver.openFileDescriptor(uri, "r").use { descriptor ->
                if (descriptor == null) return@withContext null
                PdfRenderer(descriptor).use { renderer ->
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
                    builder.toString().takeIf { it.isNotBlank() }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "OCR failed for $uri", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Ran out of memory during OCR of $uri")
            null
        }
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

    private companion object {
        const val TAG = "OcrTextExtractor"
        const val MAX_PAGES = 10
        const val TARGET_LONG_EDGE = 2000
    }
}
