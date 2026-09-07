package app.dewey.index

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.StringWriter
import kotlin.coroutines.coroutineContext

/**
 * Reads a PDF's own text layer.
 *
 * The document is walked exactly once. [PDFTextStripper] is handed the whole
 * page range and a per-page hook stops the walk as soon as [maxCharacters] is
 * reached or the caller is cancelled — the earlier version called
 * `stripper.getText(document)` once per page with `startPage`/`endPage` pinned
 * to that page, which re-walks the page tree from the start on every call and
 * is O(n²) in page count. A three-hundred-page statement holds nothing useful
 * past the first few pages for our purposes anyway, so stopping early also
 * saves the memory of stripping the rest.
 */
class PdfTextExtractor(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val maxCharacters: Int = MAX_CHARACTERS,
    private val maxBytes: Long = MAX_BYTES,
    /**
     * Where PDFBox may spill parsed document state to disk. Null falls back to
     * the JVM's default temp directory, which on some Android versions is not
     * writable — callers that have a [android.content.Context] should pass its
     * `cacheDir`.
     */
    private val cacheDir: File? = null,
) {

    data class Result(val text: String, val pageCount: Int)

    /** Thrown by [BudgetedStripper] to unwind out of PDFBox once we have enough. */
    private class TextBudgetReached : IOException("text budget reached")

    /**
     * Stops the walk as soon as [maxCharacters] is written or [isActive] says the
     * caller moved on, instead of the caller re-invoking the stripper per page.
     */
    private class BudgetedStripper(
        // Deliberately not named `output`: PDFTextStripper has a protected
        // field by that name — the writer it is handed in writeText — and a
        // Kotlin property of the same name shadows it. Same instance either
        // way, but the shadowing is a trap for the next person to touch this.
        private val sink: StringWriter,
        private val maxCharacters: Int,
        private val isActive: () -> Unit,
    ) : PDFTextStripper() {
        override fun endPage(page: PDPage) {
            super.endPage(page)
            // Checked once per page rather than once per character: cheap, and
            // still cancels promptly on a document with normal-sized pages.
            isActive()
            if (sink.buffer.length >= maxCharacters) {
                throw TextBudgetReached()
            }
        }
    }

    /**
     * @param sizeBytes the document's size, from the SAF listing. Used to refuse
     *   files too large to parse in memory — pass 0 if genuinely unknown.
     */
    suspend fun extract(uri: Uri, sizeBytes: Long = 0): Result? = withContext(io) {
        // PDFBox's default memory setting reads the whole stream into the heap
        // and its object graph runs to several times the file size. A real
        // Downloads folder contains scanned books — the corpus this was tested
        // against has a 75MB, 1012-page one — so above maxBytes we refuse here
        // and send the caller to the renderer, which works off a memory-mapped
        // descriptor and does not care how big the file is. Below that,
        // setupTempFileOnly() still keeps PDFBox's own parsed structures spilling
        // to disk instead of the heap, rather than trusting the size check alone.
        if (sizeBytes > maxBytes) {
            Log.i(TAG, "Skipping in-memory parse of $uri (${sizeBytes / 1_000_000}MB)")
            return@withContext null
        }

        // Captured once so the stripper's per-page hook — which runs from inside
        // PDFBox, not as a suspend function — can still cancel promptly.
        val activeContext = coroutineContext

        try {
            resolver.openInputStream(uri).use { stream ->
                if (stream == null) return@withContext null

                val memoryUsage = cacheDir
                    ?.let { MemoryUsageSetting.setupTempFileOnly().setTempDir(it) }
                    ?: MemoryUsageSetting.setupTempFileOnly()

                PDDocument.load(stream, memoryUsage).use { document ->
                    val pageCount = document.numberOfPages
                    val output = StringWriter()
                    val stripper = BudgetedStripper(
                        sink = output,
                        maxCharacters = maxCharacters,
                        isActive = { activeContext.ensureActive() },
                    )
                    stripper.startPage = 1
                    stripper.endPage = pageCount

                    try {
                        stripper.writeText(document, output)
                    } catch (e: TextBudgetReached) {
                        // Expected: we stopped the walk ourselves once the
                        // budget was hit. output already holds everything
                        // captured up to that point.
                    }

                    Result(output.toString().take(maxCharacters), pageCount)
                }
            }
        } catch (e: CancellationException) {
            throw e
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
         * Above this, parsing happens on the renderer instead. Lowered from 20MB:
         * setupTempFileOnly() keeps the raw bytes off the heap, but PDFBox's
         * parsed object graph for a complex document still runs to several times
         * the file size, and that part is not spillable. 8MB is chosen from what
         * a mid-range phone can hold for that graph alongside everything else
         * indexing has in memory at once (the embedder, the chunker's output).
         */
        const val MAX_BYTES = 8L * 1024 * 1024
    }
}
