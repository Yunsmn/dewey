package app.dewey.pdf

import android.content.ContentResolver
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
)
