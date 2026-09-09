package app.dewey.pdf

import android.graphics.Bitmap

/**
 * Quality presets rather than a raw 0-100 slider.
 *
 * Nobody assembling a scanned receipt into a PDF has an opinion about JPEG
 * quality 73 versus 78 — they have an opinion about "small enough to email"
 * versus "sharp enough to read the total". [longEdgePx] governs the other
 * lever that actually moves file size for a scanned page: a lot of a raster
 * PDF's weight is pixels the eye never resolves, not JPEG artifacting.
 */
enum class RasterQuality(val longEdgePx: Int, val jpegQuality: Int) {
    /** Smallest files. Legible on screen; not what you'd print. */
    SMALL(longEdgePx = 1024, jpegQuality = 55),

    /** The default: readable at arm's length without a bloated file. */
    BALANCED(longEdgePx = 1600, jpegQuality = 75),

    /** Print-worthy — roughly what a flatbed scanner gives you at 150dpi on letter paper. */
    SHARP(longEdgePx = 2480, jpegQuality = 90),
}

/** The output formats [RasterTools.pdfToImages] can produce. */
enum class RasterImageFormat(val bitmapFormat: Bitmap.CompressFormat, val extension: String) {
    /** Lossy, small, no alpha. The right default for a scanned document page. */
    JPEG(Bitmap.CompressFormat.JPEG, "jpg"),

    /** Lossless, larger, keeps alpha. For pages that are actually line art or need exact pixels. */
    PNG(Bitmap.CompressFormat.PNG, "png"),
}
