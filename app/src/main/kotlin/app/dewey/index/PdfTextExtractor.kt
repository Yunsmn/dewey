package app.dewey.index

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Reads a PDF's own text layer.
 *
 * Extraction runs a page at a time and stops once [maxCharacters] is reached.
 * A three-hundred-page statement holds nothing useful past the first few pages
 * for our purposes, and stripping all of it costs memory we do not need to spend.
 */
class PdfTextExtractor(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val maxCharacters: Int = MAX_CHARACTERS,
) {

    data class Result(val text: String, val pageCount: Int)

    suspend fun extract(uri: Uri): Result? = withContext(io) {
        try {
            resolver.openInputStream(uri).use { stream ->
                if (stream == null) return@withContext null
                PDDocument.load(stream).use { document ->
                    val pageCount = document.numberOfPages
                    val builder = StringBuilder()
                    val stripper = PDFTextStripper()

                    for (page in 1..pageCount) {
                        coroutineContext.ensureActive()
                        stripper.startPage = page
                        stripper.endPage = page
                        builder.append(stripper.getText(document))
                        if (builder.length >= maxCharacters) break
                    }
                    Result(builder.take(maxCharacters).toString(), pageCount)
                }
            }
        } catch (e: Exception) {
            // Encrypted, malformed, or not really a PDF. The caller falls back to
            // OCR, so this is a routine outcome rather than a failure.
            Log.i(TAG, "No text layer readable from $uri: ${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Ran out of memory reading $uri")
            null
        }
    }

    private companion object {
        const val TAG = "PdfTextExtractor"
        const val MAX_CHARACTERS = 200_000
    }
}
