package app.dewey.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import java.io.File

/**
 * Renders one page of a PDF for the Sign tool's placement preview.
 *
 * The same page-by-page renderer [RasterTools] is built on — see
 * [RasterPageSource] — called directly here rather than through
 * [RasterTools] itself, because placing a signature needs exactly one page's
 * bitmap rather than a whole export; going through the export machinery for
 * one page would be pure overhead with nothing to show for it.
 */
suspend fun renderPageForSignature(
    resolver: ContentResolver,
    cacheDir: File,
    source: Uri,
    sizeBytes: Long,
    pageIndex: Int,
    longEdgePx: Int,
): Result<Bitmap> {
    val pageSource = RasterPageSource(resolver, cacheDir, RasterTools.MAX_SPOOL_BYTES)
    return pageSource.withRenderer(source, sizeBytes) { renderer ->
        pageSource.renderPage(renderer, pageIndex, longEdgePx)
            ?: throw RasterToolException(RasterFailure.Unreadable(source.lastPathSegment.orEmpty()))
    }.map { it.bitmap }
}
