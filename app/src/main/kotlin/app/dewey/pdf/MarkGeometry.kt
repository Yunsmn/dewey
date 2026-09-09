package app.dewey.pdf

import kotlin.math.cos
import kotlin.math.sin

/**
 * Where to put marks on a page — watermarks and page numbers — worked out as
 * plain arithmetic with no PDFBox or Android type anywhere in it.
 *
 * Split out from MarkTools.kt so the placement math can be checked against a
 * page size and a font metric without booting PDFBox: a wrong rotation
 * formula is a silent, page-by-page visual bug, not a crash, so it is worth
 * pinning with values a human can check by hand rather than trusting it
 * because a device screenshot "looked roughly centered".
 */

/** A point in PDF user space: origin bottom-left, units are points. */
data class Point2D(val x: Float, val y: Float)

/** Where a page-number stamp goes. */
enum class Corner {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
}

/**
 * The text-matrix origin that centres a rotated watermark on the page.
 *
 * PDFBox draws text from a baseline-start point (the "origin" here) with the
 * glyphs running right and up in a local frame, then the whole frame is
 * rotated by [angleDegrees] about that same origin — so the origin is not the
 * text's own centre, and where to put it depends on the rotation.
 *
 * The fix is ordinary rotated-bounding-box math: the local box's four corners
 * are (0,0), (W,0), (0,H) and (W,H) — one of them is always the origin itself
 * — and rotating each by θ and taking the min/max gives the rotated box's
 * extent relative to the origin. Placing the origin so that box's midpoint
 * lands on the page's midpoint is what centres it, at any angle:
 *
 *   worldX(lx, ly) = lx·cosθ − ly·sinθ
 *   worldY(lx, ly) = lx·sinθ + ly·cosθ
 *
 * At θ = 0 this collapses to the obvious `(pageWidth - textWidth) / 2`, which
 * is the easiest way to sanity-check it by hand.
 */
fun watermarkPlacement(
    pageWidth: Float,
    pageHeight: Float,
    textWidth: Float,
    textHeight: Float,
    angleDegrees: Float,
): Point2D {
    val radians = Math.toRadians(angleDegrees.toDouble())
    val cosTheta = cos(radians).toFloat()
    val sinTheta = sin(radians).toFloat()

    // The origin corner (0,0) always contributes 0 and is included explicitly
    // rather than assumed, so min/max stay correct even when the other three
    // corners are all on one side of it (e.g. a 90° rotation).
    val cornerXs = floatArrayOf(
        0f,
        textWidth * cosTheta,
        -textHeight * sinTheta,
        textWidth * cosTheta - textHeight * sinTheta,
    )
    val cornerYs = floatArrayOf(
        0f,
        textWidth * sinTheta,
        textHeight * cosTheta,
        textWidth * sinTheta + textHeight * cosTheta,
    )

    val midXRelativeToOrigin = (cornerXs.min() + cornerXs.max()) / 2f
    val midYRelativeToOrigin = (cornerYs.min() + cornerYs.max()) / 2f

    return Point2D(
        x = pageWidth / 2f - midXRelativeToOrigin,
        y = pageHeight / 2f - midYRelativeToOrigin,
    )
}

/**
 * The baseline-start point for a page-number stamp in [corner].
 *
 * [margin] is the gap from the relevant page edge(s) to the text; for the
 * two "top" corners it approximates the gap to the *cap* of the glyphs by
 * measuring to the baseline directly, which overstates the visual gap by
 * roughly the font's descender. That is deliberate: PDFBox exposes a
 * string's advance width cheaply but not a measured ascent/descent for
 * arbitrary text, and a page number is short enough that a few points of
 * asymmetry between the top and bottom margins is not worth a font-metrics
 * dependency here.
 */
fun pageNumberPosition(
    corner: Corner,
    pageWidth: Float,
    pageHeight: Float,
    margin: Float,
    textWidth: Float,
): Point2D {
    val x = when (corner) {
        Corner.TOP_LEFT, Corner.BOTTOM_LEFT -> margin
        Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT -> pageWidth - margin - textWidth
        Corner.TOP_CENTER, Corner.BOTTOM_CENTER -> (pageWidth - textWidth) / 2f
    }
    val y = when (corner) {
        Corner.TOP_LEFT, Corner.TOP_CENTER, Corner.TOP_RIGHT -> pageHeight - margin
        Corner.BOTTOM_LEFT, Corner.BOTTOM_CENTER, Corner.BOTTOM_RIGHT -> margin
    }
    return Point2D(x, y)
}

/** "3" or, with a total, "3 / 12". [number] is whatever the caller chose to start counting from. */
fun formatPageNumber(number: Int, total: Int, showTotal: Boolean): String =
    if (showTotal) "$number / $total" else number.toString()
