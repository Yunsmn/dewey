package app.dewey.ui.tools.sign

import app.dewey.pdf.Corner
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The screen-space placement math behind Sign: where a chosen signature
 * starts, how dragging and resizing move it, and how the corner shortcuts
 * work — all in normalized 0..1 page fractions, checked without a device or
 * a real bitmap. [app.dewey.pdf.SignatureStampTest] is the sibling that
 * checks what happens to a placement once it reaches the page's own rotated
 * coordinate space; this file stops at the boundary before that.
 */
class SignPlacementMathTest {

    private val tolerance = 0.001f

    @Test
    fun `a default placement sizes to the requested fraction of the page width`() {
        val placement = defaultPlacement(
            boxWidthPx = 1000f,
            boxHeightPx = 2000f,
            signatureWidthPx = 200,
            signatureHeightPx = 100,
            sizeFraction = 0.4f,
        )
        assertThat(placement.width).isWithin(tolerance).of(0.4f)
    }

    @Test
    fun `a default placement's height follows the signature's own aspect ratio, not the page's`() {
        // A 2:1 signature on a page 1000x2000 (1:2): width fraction 0.4 means
        // 400px wide, which at the signature's own aspect ratio is 200px
        // tall — 0.1 of the page's own 2000px height.
        val placement = defaultPlacement(
            boxWidthPx = 1000f,
            boxHeightPx = 2000f,
            signatureWidthPx = 200,
            signatureHeightPx = 100,
            sizeFraction = 0.4f,
        )
        assertThat(placement.height).isWithin(tolerance).of(0.1f)
    }

    @Test
    fun `a default placement is centered on the page`() {
        val placement = defaultPlacement(
            boxWidthPx = 1000f,
            boxHeightPx = 2000f,
            signatureWidthPx = 200,
            signatureHeightPx = 100,
            sizeFraction = 0.4f,
        )
        assertThat(placement.centerX).isWithin(tolerance).of(0.5f)
        assertThat(placement.centerY).isWithin(tolerance).of(0.5f)
    }

    @Test
    fun `an unknown box or signature size falls back to a square-ish placement rather than dividing by zero`() {
        val placement = defaultPlacement(
            boxWidthPx = 0f,
            boxHeightPx = 0f,
            signatureWidthPx = 0,
            signatureHeightPx = 0,
            sizeFraction = 0.3f,
        )
        assertThat(placement.width).isWithin(tolerance).of(0.3f)
        assertThat(placement.height).isWithin(tolerance).of(0.3f)
    }

    @Test
    fun `resizing keeps the placement's center in place while its size changes`() {
        val original = defaultPlacement(1000f, 1000f, 200, 100, sizeFraction = 0.3f)

        val resized = resizePlacement(
            current = original,
            sizeFraction = 0.6f,
            boxWidthPx = 1000f,
            boxHeightPx = 1000f,
            signatureWidthPx = 200,
            signatureHeightPx = 100,
        )

        assertThat(resized.centerX).isWithin(tolerance).of(original.centerX)
        assertThat(resized.centerY).isWithin(tolerance).of(original.centerY)
        assertThat(resized.width).isWithin(tolerance).of(0.6f)
    }

    @Test
    fun `dragging moves the placement by the given fraction`() {
        val start = SignPlacement(x = 0.3f, y = 0.3f, width = 0.2f, height = 0.1f)

        val moved = dragPlacement(start, deltaXFraction = 0.1f, deltaYFraction = -0.05f)

        assertThat(moved.x).isWithin(tolerance).of(0.4f)
        assertThat(moved.y).isWithin(tolerance).of(0.25f)
        assertThat(moved.width).isWithin(tolerance).of(0.2f)
    }

    @Test
    fun `dragging past the page edge is clamped rather than allowed to leave the page`() {
        val start = SignPlacement(x = 0.9f, y = 0.9f, width = 0.2f, height = 0.2f)

        val moved = dragPlacement(start, deltaXFraction = 0.5f, deltaYFraction = 0.5f)

        // Width/height are 0.2, so the furthest valid top-left is 0.8.
        assertThat(moved.x).isWithin(tolerance).of(0.8f)
        assertThat(moved.y).isWithin(tolerance).of(0.8f)
    }

    @Test
    fun `dragging off the top-left edge is clamped to zero`() {
        val start = SignPlacement(x = 0.05f, y = 0.05f, width = 0.2f, height = 0.2f)

        val moved = dragPlacement(start, deltaXFraction = -0.5f, deltaYFraction = -0.5f)

        assertThat(moved.x).isWithin(tolerance).of(0f)
        assertThat(moved.y).isWithin(tolerance).of(0f)
    }

    @Test
    fun `each corner shortcut moves the placement near that corner, size unchanged`() {
        val start = SignPlacement(x = 0.4f, y = 0.4f, width = 0.2f, height = 0.1f)

        val topLeft = cornerPlacement(start, Corner.TOP_LEFT)
        assertThat(topLeft.x).isLessThan(0.1f)
        assertThat(topLeft.y).isLessThan(0.1f)
        assertThat(topLeft.width).isWithin(tolerance).of(start.width)
        assertThat(topLeft.height).isWithin(tolerance).of(start.height)

        val bottomRight = cornerPlacement(start, Corner.BOTTOM_RIGHT)
        assertThat(bottomRight.x + bottomRight.width).isGreaterThan(0.9f)
        assertThat(bottomRight.y + bottomRight.height).isGreaterThan(0.9f)

        val topCenter = cornerPlacement(start, Corner.TOP_CENTER)
        assertThat(topCenter.centerX).isWithin(tolerance).of(0.5f)
        assertThat(topCenter.y).isLessThan(0.1f)
    }

    @Test
    fun `a corner placement never leaves the page even for a large signature`() {
        val large = SignPlacement(x = 0.1f, y = 0.1f, width = 0.9f, height = 0.9f)

        val placed = cornerPlacement(large, Corner.BOTTOM_RIGHT)

        assertThat(placed.x).isAtLeast(0f)
        assertThat(placed.y).isAtLeast(0f)
        assertThat(placed.x + placed.width).isAtMost(1f + tolerance)
        assertThat(placed.y + placed.height).isAtMost(1f + tolerance)
    }
}
