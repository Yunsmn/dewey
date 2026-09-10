package app.dewey.ui.tools.raster

import app.dewey.pdf.RasterImageFormat
import app.dewey.pdf.RasterQuality

/**
 * The word a person picks between, never the pixel count or JPEG quality
 * behind it — see [RasterQuality]'s own doc for why the presets exist at all.
 */
fun RasterQuality.label(): String = when (this) {
    RasterQuality.SMALL -> "Small"
    RasterQuality.BALANCED -> "Balanced"
    RasterQuality.SHARP -> "Sharp"
}

/** The format's name, spelled the way a person reads it rather than a MIME type. */
fun RasterImageFormat.label(): String = when (this) {
    RasterImageFormat.JPEG -> "JPEG"
    RasterImageFormat.PNG -> "PNG"
}
