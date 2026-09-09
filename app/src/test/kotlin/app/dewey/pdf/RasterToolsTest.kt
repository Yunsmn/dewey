package app.dewey.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [RasterGeometry.fitInto] is the one piece of "images to PDF" that runs on
 * every image placed, and getting it wrong stretches or crops every document
 * built from photos rather than just one. It has no Android dependency
 * precisely so this can run on a plain JVM instead of trusting a device
 * screenshot to catch a distortion.
 */
class RasterToolsGeometryTest {

    @Test
    fun `a landscape image into a portrait page is letterboxed, not stretched`() {
        // 2000x1000 (2:1) into 600x800 (3:4). Width is the binding constraint:
        // 600/2000 = 0.3, which gives height 300 — well short of 800 tall.
        val placement = RasterGeometry.fitInto(
            imageWidth = 2000f,
            imageHeight = 1000f,
            pageWidth = 600f,
            pageHeight = 800f,
        )
        assertThat(placement.width).isEqualTo(600f)
        assertThat(placement.height).isEqualTo(300f)
        // Centered top-to-bottom in the leftover space.
        assertThat(placement.x).isEqualTo(0f)
        assertThat(placement.y).isEqualTo(250f)
    }

    @Test
    fun `a portrait image into a landscape page is pillarboxed, not stretched`() {
        // 1000x2000 (1:2) into 800x600. Height is the binding constraint:
        // 600/2000 = 0.3, which gives width 300 — well short of 800 wide.
        val placement = RasterGeometry.fitInto(
            imageWidth = 1000f,
            imageHeight = 2000f,
            pageWidth = 800f,
            pageHeight = 600f,
        )
        assertThat(placement.width).isEqualTo(300f)
        assertThat(placement.height).isEqualTo(600f)
        assertThat(placement.x).isEqualTo(250f)
        assertThat(placement.y).isEqualTo(0f)
    }

    @Test
    fun `an image whose aspect ratio matches the page fills it exactly, uncentered`() {
        val placement = RasterGeometry.fitInto(
            imageWidth = 400f,
            imageHeight = 300f,
            pageWidth = 800f,
            pageHeight = 600f,
        )
        assertThat(placement.width).isEqualTo(800f)
        assertThat(placement.height).isEqualTo(600f)
        assertThat(placement.x).isEqualTo(0f)
        assertThat(placement.y).isEqualTo(0f)
    }

    @Test
    fun `an image larger than the page is scaled down to fit`() {
        val placement = RasterGeometry.fitInto(
            imageWidth = 4000f,
            imageHeight = 3000f,
            pageWidth = 800f,
            pageHeight = 800f,
        )
        assertThat(placement.width).isAtMost(800f)
        assertThat(placement.height).isAtMost(800f)
        // The image is 4:3 — the fit should preserve that, not just clamp.
        assertThat(placement.width / placement.height).isWithin(0.001f).of(4000f / 3000f)
    }

    @Test
    fun `an image smaller than the page is scaled up to fill it`() {
        // The decision: this exists to turn phone photos into full document
        // pages, so a small source image should not sit at native size in
        // the middle of a mostly-blank page. It gets scaled up like anything
        // else, centered, aspect ratio intact.
        val placement = RasterGeometry.fitInto(
            imageWidth = 100f,
            imageHeight = 100f,
            pageWidth = 800f,
            pageHeight = 800f,
        )
        assertThat(placement.width).isEqualTo(800f)
        assertThat(placement.height).isEqualTo(800f)
    }

    @Test
    fun `placement always keeps the source aspect ratio`() {
        val placement = RasterGeometry.fitInto(
            imageWidth = 1500f,
            imageHeight = 900f,
            pageWidth = 612f,
            pageHeight = 792f,
        )
        val sourceRatio = 1500f / 900f
        val placedRatio = placement.width / placement.height
        assertThat(placedRatio).isWithin(0.0001f).of(sourceRatio)
    }

    @Test
    fun `a placement is always centered on the page`() {
        val placement = RasterGeometry.fitInto(
            imageWidth = 300f,
            imageHeight = 200f,
            pageWidth = 612f,
            pageHeight = 792f,
        )
        val leftover = 612f - placement.width
        assertThat(placement.x).isWithin(0.001f).of(leftover / 2f)
    }

    @Test
    fun `zero image width places nothing rather than dividing by zero`() {
        val placement = RasterGeometry.fitInto(
            imageWidth = 0f,
            imageHeight = 500f,
            pageWidth = 612f,
            pageHeight = 792f,
        )
        assertThat(placement.width).isEqualTo(0f)
        assertThat(placement.height).isEqualTo(0f)
        assertThat(placement.width.isNaN()).isFalse()
    }

    @Test
    fun `zero image height places nothing rather than dividing by zero`() {
        val placement = RasterGeometry.fitInto(
            imageWidth = 500f,
            imageHeight = 0f,
            pageWidth = 612f,
            pageHeight = 792f,
        )
        assertThat(placement.width).isEqualTo(0f)
        assertThat(placement.height).isEqualTo(0f)
    }

    @Test
    fun `zero page dimensions place nothing rather than a division by zero`() {
        val placement = RasterGeometry.fitInto(
            imageWidth = 500f,
            imageHeight = 500f,
            pageWidth = 0f,
            pageHeight = 792f,
        )
        assertThat(placement.width).isEqualTo(0f)
        assertThat(placement.height).isEqualTo(0f)
        assertThat(placement.x).isEqualTo(0f)
        assertThat(placement.y).isEqualTo(0f)
    }

    @Test
    fun `negative dimensions place nothing rather than a nonsensical negative size`() {
        val placement = RasterGeometry.fitInto(
            imageWidth = -100f,
            imageHeight = 500f,
            pageWidth = 612f,
            pageHeight = 792f,
        )
        assertThat(placement.width).isEqualTo(0f)
        assertThat(placement.height).isEqualTo(0f)
    }

    @Test
    fun `a one pixel image still gets a sane, non-infinite placement`() {
        val placement = RasterGeometry.fitInto(
            imageWidth = 1f,
            imageHeight = 1f,
            pageWidth = 612f,
            pageHeight = 792f,
        )
        assertThat(placement.width).isEqualTo(612f)
        assertThat(placement.height).isEqualTo(612f)
        assertThat(placement.width.isFinite()).isTrue()
    }
}

/**
 * [CompressionResult] is how [RasterTools.compress] admits a result that
 * came back larger than the original instead of quietly calling it done.
 */
class CompressionResultTest {

    @Test
    fun `a smaller result reports the bytes saved and isSmaller true`() {
        val result = CompressionResult(originalBytes = 1_000_000, resultBytes = 400_000)
        assertThat(result.savedBytes).isEqualTo(600_000)
        assertThat(result.isSmaller).isTrue()
    }

    @Test
    fun `a larger result is reported honestly, not hidden`() {
        // A text-heavy PDF with little embedded imagery: rasterizing it can
        // make it bigger, and the caller needs to be able to tell.
        val result = CompressionResult(originalBytes = 100_000, resultBytes = 250_000)
        assertThat(result.savedBytes).isEqualTo(-150_000)
        assertThat(result.isSmaller).isFalse()
    }

    @Test
    fun `an unknown result size reports unknown rather than a false comparison`() {
        val result = CompressionResult(originalBytes = 1_000_000, resultBytes = null)
        assertThat(result.savedBytes).isNull()
        assertThat(result.isSmaller).isNull()
    }
}
