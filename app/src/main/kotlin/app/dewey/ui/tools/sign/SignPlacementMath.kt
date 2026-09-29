package app.dewey.ui.tools.sign

import app.dewey.pdf.Corner

/**
 * Where the signature sits on the previewed page: normalized 0..1 against the
 * preview's own displayed size, top-left origin, the same convention a drag
 * gesture's own offsets use. Kept entirely free of PDF or Android types so it
 * can be checked as plain arithmetic — see SignPlacementMathTest — before it
 * is ever handed to [app.dewey.pdf.signaturePlacement] for the separate job
 * of landing in the page's own rotated coordinate space.
 */
data class SignPlacement(val x: Float, val y: Float, val width: Float, val height: Float) {
    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f
}

private const val DEFAULT_SIZE_FRACTION = 0.32f
private const val CORNER_MARGIN = 0.04f

/**
 * The starting size and position for a signature that was just chosen: sized
 * to [sizeFraction] of the page's own displayed width, with its height
 * following the *signature's* aspect ratio rather than the page's — a wide,
 * short signature stamped at a square size would come out squashed — and
 * centered, the least surprising place to start dragging it from.
 */
fun defaultPlacement(
    boxWidthPx: Float,
    boxHeightPx: Float,
    signatureWidthPx: Int,
    signatureHeightPx: Int,
    sizeFraction: Float = DEFAULT_SIZE_FRACTION,
): SignPlacement {
    val normalizedHeight = normalizedHeightFor(boxWidthPx, boxHeightPx, signatureWidthPx, signatureHeightPx, sizeFraction)
    return SignPlacement(
        x = (1f - sizeFraction) / 2f,
        y = (1f - normalizedHeight) / 2f,
        width = sizeFraction,
        height = normalizedHeight,
    )
}

/**
 * [current] resized to [sizeFraction] of the page's displayed width, its
 * aspect ratio preserved and its center held in place rather than its
 * top-left corner, so dragging the size slider grows a signature outward
 * from where it already sits instead of sliding it toward the corner.
 */
fun resizePlacement(
    current: SignPlacement,
    sizeFraction: Float,
    boxWidthPx: Float,
    boxHeightPx: Float,
    signatureWidthPx: Int,
    signatureHeightPx: Int,
): SignPlacement {
    val normalizedHeight = normalizedHeightFor(boxWidthPx, boxHeightPx, signatureWidthPx, signatureHeightPx, sizeFraction)
    val centerX = current.centerX
    val centerY = current.centerY
    return clamped(
        SignPlacement(
            x = centerX - sizeFraction / 2f,
            y = centerY - normalizedHeight / 2f,
            width = sizeFraction,
            height = normalizedHeight,
        ),
    )
}

private fun normalizedHeightFor(
    boxWidthPx: Float,
    boxHeightPx: Float,
    signatureWidthPx: Int,
    signatureHeightPx: Int,
    sizeFraction: Float,
): Float {
    if (boxWidthPx <= 0f || boxHeightPx <= 0f || signatureWidthPx <= 0 || signatureHeightPx <= 0) {
        return sizeFraction
    }
    val widthPx = boxWidthPx * sizeFraction
    val heightPx = widthPx * (signatureHeightPx.toFloat() / signatureWidthPx.toFloat())
    return heightPx / boxHeightPx
}

/** [current] moved by a drag of ([deltaXFraction], [deltaYFraction]), kept fully on the page. */
fun dragPlacement(current: SignPlacement, deltaXFraction: Float, deltaYFraction: Float): SignPlacement =
    clamped(current.copy(x = current.x + deltaXFraction, y = current.y + deltaYFraction))

/** [current] moved to one of [app.dewey.ui.tools.secure.CornerGrid]'s six quick positions, size unchanged. */
fun cornerPlacement(current: SignPlacement, corner: Corner): SignPlacement {
    val x = when (corner) {
        Corner.TOP_LEFT, Corner.BOTTOM_LEFT -> CORNER_MARGIN
        Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT -> 1f - CORNER_MARGIN - current.width
        Corner.TOP_CENTER, Corner.BOTTOM_CENTER -> (1f - current.width) / 2f
    }
    val y = when (corner) {
        Corner.TOP_LEFT, Corner.TOP_CENTER, Corner.TOP_RIGHT -> CORNER_MARGIN
        Corner.BOTTOM_LEFT, Corner.BOTTOM_CENTER, Corner.BOTTOM_RIGHT -> 1f - CORNER_MARGIN - current.height
    }
    return clamped(current.copy(x = x, y = y))
}

/** Keeps a placement fully within the 0..1 page, sliding rather than shrinking it back on screen. */
private fun clamped(placement: SignPlacement): SignPlacement = placement.copy(
    x = placement.x.coerceIn(0f, (1f - placement.width).coerceAtLeast(0f)),
    y = placement.y.coerceIn(0f, (1f - placement.height).coerceAtLeast(0f)),
)
