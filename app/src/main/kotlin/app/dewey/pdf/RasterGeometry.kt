package app.dewey.pdf

/**
 * Fits one rectangle inside another without distorting its aspect ratio.
 *
 * Pure and Android-free on purpose. It backs "images to PDF", where a
 * stretched receipt photo is a bug nobody notices until they try to read the
 * total, so the math is worth a JVM unit test rather than trusting a device
 * screenshot.
 */
object RasterGeometry {

    /** Where and how large an image lands on a page it is centered in, in the page's own units. */
    data class Placement(val width: Float, val height: Float, val x: Float, val y: Float)

    /**
     * Scales an [imageWidth]x[imageHeight] rectangle to fill as much of a
     * [pageWidth]x[pageHeight] page as it can without cropping or distorting
     * it, then centers the result.
     *
     * Deliberately scales up as well as down. The alternative — only ever
     * shrinking, so a small image sits at native size in the middle of the
     * page — is wrong for the case this exists to serve: a phone photo of a
     * receipt, scanned small to save space, should still fill the page it
     * becomes rather than appear as a postage stamp surrounded by white.
     *
     * Any of the four inputs being zero or negative describes nothing to
     * place. Rather than divide by zero and hand the caller NaN or Infinity,
     * that case returns a zero-sized placement at the origin.
     */
    fun fitInto(
        imageWidth: Float,
        imageHeight: Float,
        pageWidth: Float,
        pageHeight: Float,
    ): Placement {
        if (imageWidth <= 0f || imageHeight <= 0f || pageWidth <= 0f || pageHeight <= 0f) {
            return Placement(width = 0f, height = 0f, x = 0f, y = 0f)
        }

        val scale = minOf(pageWidth / imageWidth, pageHeight / imageHeight)
        val width = imageWidth * scale
        val height = imageHeight * scale

        return Placement(
            width = width,
            height = height,
            x = (pageWidth - width) / 2f,
            y = (pageHeight - height) / 2f,
        )
    }
}
