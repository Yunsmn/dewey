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
    private val maxBytes: Long = MAX_BYTES,
) {

    data class Result(val text: String, val pageCount: Int)

    /**
     * @param sizeBytes the document's size, from the SAF listing. Used to refuse
     *   files too large to parse in memory — pass 0 if genuinely unknown.
     */
    suspend fun extract(uri: Uri, sizeBytes: Long = 0): Result? = withContext(io) {
        // PDFBox reads the whole stream into the heap. A real Downloads folder
        // contains scanned books — the corpus this was tested against has a 75MB,
        // 1012-page one — and loading that is an OutOfMemoryError, not a slow
        // path. Refusing here sends the caller to the renderer, which works off a
        // memory-mapped descriptor and does not care how big the file is.
        if (sizeBytes > maxBytes) {
            Log.i(TAG, "Skipping in-memory parse of $uri (${sizeBytes / 1_000_000}MB)")
            return@withContext null
        }

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

        /**
         * Above this, parsing happens on the renderer instead. Chosen from what
         * a mid-range phone can hold alongside PDFBox's own object graph, which
         * runs to several times the file size.
         */
        const val MAX_BYTES = 20L * 1024 * 1024
    }
}
