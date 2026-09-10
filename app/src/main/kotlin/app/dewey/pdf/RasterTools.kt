package app.dewey.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.coroutineContext

/** What went wrong, in words a screen can show without translation. Mirrors [PdfWorkspace.Failure]'s shape. */
sealed interface RasterFailure {
    data class TooLarge(val name: String) : RasterFailure
    data class Unreadable(val name: String) : RasterFailure
    data class Protected(val name: String) : RasterFailure
    data class WriteFailed(val reason: String) : RasterFailure
}

/** Carries a [RasterFailure] through Kotlin's [Result]. */
class RasterToolException(val failure: RasterFailure) : Exception(failure.toString())

/**
 * What an export to images actually produced.
 *
 * [skipped] exists because a count alone cannot say whether it is complete.
 * Asked for every page of a twenty-page document, an export that quietly
 * failed to render page fourteen used to come back as a successful 19 — and a
 * caller who never asked for a specific list has nothing to compare 19 against.
 * The indices are 0-based, matching the pageIndex handed to onPage.
 */
data class PageExport(val rendered: Int, val skipped: List<Int>) {
    val isComplete: Boolean get() = skipped.isEmpty()
}

/**
 * What [RasterTools.compress] achieved, so the caller can say so rather than
 * silently accepting whatever came out.
 *
 * [resultBytes] is null when the provider does not report a size after the
 * write — the same "unknown, not empty" gap [app.dewey.sort.DocumentMover]
 * already has to live with — in which case [isSmaller] is also null rather
 * than a guess.
 */
data class CompressionResult(val originalBytes: Long, val resultBytes: Long?) {
    val savedBytes: Long? get() = resultBytes?.let { originalBytes - it }
    val isSmaller: Boolean? get() = resultBytes?.let { it < originalBytes }
}

/**
 * PDF-to-images, images-to-PDF, and PDF compression, all built on the same
 * constraint: a phone does not have room to hold more than one decoded page
 * or image in memory at a time. See [RasterPageSource] for how a source PDF
 * is rendered page by page, and [PdfWorkspace] for how a result is opened and
 * saved — this class does neither of those itself.
 */
class RasterTools(
    private val resolver: ContentResolver,
    private val workspace: PdfWorkspace,
    cacheDir: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    maxSpoolBytes: Long = MAX_SPOOL_BYTES,
) {

    private val pageSource = RasterPageSource(resolver, cacheDir, maxSpoolBytes)

    /**
     * Renders each page of [uri] to a compressed image and hands it to
     * [onPage], one page at a time, in order.
     *
     * [onPage] decides where the bytes go — a SAF tree, a zip, a preview
     * cache — this function only knows how to turn a page into pixels
     * safely. At most one page's bitmap and one page's encoded bytes exist at
     * once: the bitmap is recycled the moment [onPage] returns, so a
     * three-hundred-page statement costs one page of memory, not three
     * hundred.
     *
     * @param pages null renders every page; otherwise only these zero-based
     *   indices, in the order given. An index outside the document is
     *   skipped rather than failing the whole call.
     * @return how many pages were rendered and which were skipped — see
     *   [PageExport]. A write that fails inside [onPage] fails the whole call
     *   as [RasterFailure.WriteFailed], not as an unreadable source.
     */
    suspend fun pdfToImages(
        uri: Uri,
        sizeBytes: Long = 0,
        format: RasterImageFormat = RasterImageFormat.JPEG,
        quality: RasterQuality = RasterQuality.BALANCED,
        pages: List<Int>? = null,
        onPage: suspend (pageIndex: Int, bytes: ByteArray) -> Unit,
    ): Result<PageExport> = withContext(io) {
        pageSource.withRenderer(uri, sizeBytes) { renderer ->
            val indices = pages ?: (0 until renderer.pageCount)
            var rendered = 0
            val skipped = mutableListOf<Int>()
            for (index in indices) {
                coroutineContext.ensureActive()
                if (index !in 0 until renderer.pageCount) {
                    skipped += index
                    continue
                }
                val page = pageSource.renderPage(renderer, index, quality.longEdgePx)
                if (page == null) {
                    skipped += index
                    continue
                }
                val bytes = try {
                    encode(page.bitmap, format, quality.jpegQuality)
                } finally {
                    page.bitmap.recycle()
                }
                if (bytes == null) {
                    skipped += index
                    continue
                }
                try {
                    onPage(index, bytes)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The caller's write, not our render. Named as such so
                    // withRenderer passes it through instead of calling the
                    // source unreadable.
                    throw RasterToolException(
                        RasterFailure.WriteFailed(e.message ?: "could not save page ${index + 1}")
                    )
                }
                rendered++
            }
            PageExport(rendered = rendered, skipped = skipped.toList())
        }
    }

    /**
     * Assembles [imageUris] into a new PDF at [targetUri], one image per
     * page, each fitted to [pageSize] without distortion — see
     * [RasterGeometry.fitInto] for the placement math, which is what actually
     * decides whether a landscape photo ends up sideways-cropped or properly
     * letterboxed.
     *
     * Images are decoded one at a time, at a resolution already downsampled
     * toward [quality]'s target so a full-resolution phone photo (commonly
     * 12+ megapixels) is never fully decoded just to be shrunk afterward.
     *
     * @return the number of images actually placed. Unreadable images are
     *   skipped rather than failing the whole document; the call only fails
     *   if none of them could be placed.
     */
    suspend fun imagesToPdf(
        imageUris: List<Uri>,
        targetUri: Uri,
        pageSize: PDRectangle = PDRectangle.LETTER,
        quality: RasterQuality = RasterQuality.BALANCED,
    ): Result<Int> = withContext(io) {
        if (imageUris.isEmpty()) {
            return@withContext Result.failure(RasterToolException(RasterFailure.Unreadable("no images given")))
        }

        val document = workspace.newDocument()
        try {
            var placed = 0
            for (imageUri in imageUris) {
                coroutineContext.ensureActive()
                val bitmap = decodeScaled(imageUri, quality.longEdgePx) ?: continue
                try {
                    placeImage(document, bitmap, pageSize, quality.jpegQuality)
                    placed++
                } finally {
                    bitmap.recycle()
                }
            }

            if (placed == 0) {
                return@withContext Result.failure(
                    RasterToolException(RasterFailure.Unreadable("none of the given images could be read"))
                )
            }

            workspace.write(document, targetUri).map { placed }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not assemble images into $targetUri", e)
            Result.failure(RasterToolException(RasterFailure.WriteFailed(e.message ?: "could not assemble PDF")))
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Ran out of memory assembling images into $targetUri")
            Result.failure(RasterToolException(RasterFailure.WriteFailed("ran out of memory")))
        } finally {
            document.close()
        }
    }

    /**
     * Rewrites [uri] as a new PDF at [targetUri] whose pages are downsampled,
     * re-encoded raster images rather than the original content stream —
     * the same render-and-recycle pipeline as [pdfToImages], reassembled the
     * way [imagesToPdf] assembles external photos, except each page keeps
     * its own source page's proportions instead of being fit to a common
     * page size.
     *
     * This only shrinks documents whose weight is pixels — a scanned book.
     * A text-heavy PDF with little embedded imagery has nothing worth
     * downsampling and can come back *larger*, because it is now a raster
     * image of its text rather than the text itself. [CompressionResult] is
     * returned instead of silently accepting that outcome; a caller that
     * only wants files smaller than the original can check
     * [CompressionResult.isSmaller] and offer to keep the original.
     */
    suspend fun compress(
        uri: Uri,
        sizeBytes: Long,
        targetUri: Uri,
        quality: RasterQuality = RasterQuality.SMALL,
    ): Result<CompressionResult> = withContext(io) {
        val document = workspace.newDocument()
        try {
            val rendered = pageSource.withRenderer(uri, sizeBytes) { renderer ->
                var pages = 0
                for (index in 0 until renderer.pageCount) {
                    coroutineContext.ensureActive()
                    val page = pageSource.renderPage(renderer, index, quality.longEdgePx) ?: continue
                    try {
                        val pdPage = PDPage(PDRectangle(page.pageWidthPt, page.pageHeightPt))
                        document.addPage(pdPage)
                        val xObject = JPEGFactory.createFromImage(document, page.bitmap, quality.jpegQuality / 100f)
                        PDPageContentStream(document, pdPage).use { stream ->
                            stream.drawImage(xObject, 0f, 0f, page.pageWidthPt, page.pageHeightPt)
                        }
                        pages++
                    } finally {
                        page.bitmap.recycle()
                    }
                }
                pages
            }

            val pageCount = rendered.getOrElse { return@withContext Result.failure(it) }
            if (pageCount == 0) {
                return@withContext Result.failure(
                    RasterToolException(RasterFailure.Unreadable(uri.lastPathSegment.orEmpty()))
                )
            }

            workspace.write(document, targetUri)
                .map { CompressionResult(originalBytes = sizeBytes, resultBytes = sizeOf(targetUri)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not compress $uri", e)
            Result.failure(RasterToolException(RasterFailure.WriteFailed(e.message ?: "could not compress PDF")))
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Ran out of memory compressing $uri")
            Result.failure(RasterToolException(RasterFailure.WriteFailed("ran out of memory")))
        } finally {
            document.close()
        }
    }

    /** Null means [Bitmap.compress] itself reported failure — an undersized page is skipped, not sent on empty. */
    private fun encode(bitmap: Bitmap, format: RasterImageFormat, quality: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val ok = bitmap.compress(format.bitmapFormat, quality, output)
        return if (ok) output.toByteArray() else null
    }

    private fun placeImage(document: PDDocument, bitmap: Bitmap, pageSize: PDRectangle, jpegQuality: Int) {
        val placement = RasterGeometry.fitInto(
            imageWidth = bitmap.width.toFloat(),
            imageHeight = bitmap.height.toFloat(),
            pageWidth = pageSize.width,
            pageHeight = pageSize.height,
        )
        val page = PDPage(pageSize)
        document.addPage(page)
        val xObject = imageXObject(document, bitmap, jpegQuality)
        PDPageContentStream(document, page).use { stream ->
            stream.drawImage(xObject, placement.x, placement.y, placement.width, placement.height)
        }
    }

    /**
     * PNG sources are usually screenshots or line art where alpha and exact
     * pixels matter, so they go through the lossless path; everything else
     * (in practice, camera JPEGs) is re-encoded at [jpegQuality] rather than
     * embedded losslessly, which for a photo would multiply the PDF's size
     * for no visible gain.
     */
    private fun imageXObject(document: PDDocument, bitmap: Bitmap, jpegQuality: Int): PDImageXObject =
        if (bitmap.hasAlpha()) {
            LosslessFactory.createFromImage(document, bitmap)
        } else {
            JPEGFactory.createFromImage(document, bitmap, jpegQuality / 100f)
        }

    /**
     * Decodes [uri] downsampled toward [longEdgePx] using
     * [BitmapFactory.Options.inSampleSize], reading only the image's
     * dimensions first. A phone photo is routinely 12+ megapixels; decoding
     * it in full just to immediately shrink it is the kind of avoidable
     * allocation that turns "convert ten photos" into an OutOfMemoryError.
     */
    private fun decodeScaled(uri: Uri, longEdgePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null

        val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (longestSide <= 0) return null

        var sampleSize = 1
        while (longestSide / (sampleSize * 2) >= longEdgePx) sampleSize *= 2

        val decode = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decode) }
    }

    /**
     * A document's size in bytes, or null when the provider does not say —
     * COLUMN_SIZE is allowed to come back null, meaning "unknown", not zero.
     * See DocumentMover's own private sizeOf for the same gap on the read
     * side.
     */
    private fun sizeOf(uri: Uri): Long? = try {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the size of $uri", e)
        null
    }

    companion object {
        private const val TAG = "RasterTools"

        /**
         * Above this, a source PDF is refused rather than copied to the
         * cache to work around a non-seekable descriptor. Matches
         * [OcrTextExtractor]'s reasoning: the copy fallback writes to the
         * app's cache, so it has to stay bounded regardless of how big the
         * user's document is.
         */
        const val MAX_SPOOL_BYTES = 40L * 1024 * 1024
    }
}
