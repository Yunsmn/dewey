package app.dewey.pdf

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Opening and saving PDFs, for every tool that edits one.
 *
 * Shared so the awkward parts are solved once. Each of them is a bug this
 * project has already paid for somewhere else:
 *
 *  - **Parsed state spills to disk, not the heap.** PDFBox's default reads the
 *    whole document into memory and its object graph runs to several times the
 *    file size. See app.dewey.index.PdfTextExtractor, where a 75MB scan was an
 *    OutOfMemoryError rather than a slow path.
 *  - **The temp directory is passed explicitly.** The JVM default is not
 *    reliably writable on Android.
 *  - **A result is written to a new document, never over the input.** Every
 *    tool here is destructive to somebody's file if it goes wrong halfway, and
 *    a half-written PDF over the original is unrecoverable.
 *  - **Size is checked before opening.** A tool asked to merge a folder of
 *    scanned books should refuse rather than die.
 */
class PdfWorkspace(
    private val resolver: ContentResolver,
    private val cacheDir: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val maxBytes: Long = MAX_BYTES,
) {

    /** What went wrong, in words a screen can show without translation. */
    sealed interface Failure {
        data class TooLarge(val name: String) : Failure
        data class Unreadable(val name: String) : Failure
        data class Encrypted(val name: String) : Failure
        data class CouldNotWrite(val reason: String) : Failure
    }

    /**
     * Opens [uri] and hands the document to [block], closing it afterwards
     * whatever happens.
     *
     * The document is only valid inside [block]. Returning it would be a use
     * after close, and PDFBox fails that lazily and confusingly.
     */
    suspend fun <T> read(uri: Uri, sizeBytes: Long = 0, block: (PDDocument) -> T): Result<T> =
        withContext(io) {
            if (sizeBytes > maxBytes) {
                return@withContext Result.failure(
                    PdfToolException(Failure.TooLarge(uri.lastPathSegment.orEmpty()))
                )
            }
            try {
                resolver.openInputStream(uri).use { stream ->
                    if (stream == null) {
                        return@withContext Result.failure(
                            PdfToolException(Failure.Unreadable(uri.lastPathSegment.orEmpty()))
                        )
                    }
                    PDDocument.load(stream, memoryUsage()).use { document ->
                        if (document.isEncrypted) {
                            return@withContext Result.failure(
                                PdfToolException(Failure.Encrypted(uri.lastPathSegment.orEmpty()))
                            )
                        }
                        Result.success(block(document))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read $uri", e)
                Result.failure(PdfToolException(Failure.Unreadable(uri.lastPathSegment.orEmpty())))
            }
        }

    /** Writes [document] to [target]. The caller owns closing [document]. */
    suspend fun write(document: PDDocument, target: Uri): Result<Unit> = withContext(io) {
        try {
            resolver.openOutputStream(target, "wt").use { out ->
                if (out == null) {
                    return@withContext Result.failure(
                        PdfToolException(Failure.CouldNotWrite("could not open $target"))
                    )
                }
                document.save(out)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not write $target", e)
            Result.failure(PdfToolException(Failure.CouldNotWrite(e.message ?: "write failed")))
        }
    }

    /** A blank document that spills to the same cache the readers use. */
    fun newDocument(): PDDocument = PDDocument(memoryUsage())

    private fun memoryUsage(): MemoryUsageSetting =
        MemoryUsageSetting.setupTempFileOnly().setTempDir(cacheDir)

    companion object {
        private const val TAG = "PdfWorkspace"

        /**
         * Above this a tool refuses rather than tries. Larger than the indexer's
         * 8MB ceiling because these tools are one document at a time and
         * user-initiated, where indexing is four hundred in a row in the
         * background — but still far below what would exhaust a phone.
         */
        const val MAX_BYTES = 60L * 1024 * 1024
    }
}

/** Carries a [PdfWorkspace.Failure] through Kotlin's [Result]. */
class PdfToolException(val failure: PdfWorkspace.Failure) : Exception(failure.toString())
