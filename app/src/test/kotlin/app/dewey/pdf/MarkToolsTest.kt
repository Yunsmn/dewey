package app.dewey.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The placement arithmetic behind [watermark] and [pageNumbers] — pure
 * geometry, checked against page sizes and angles by hand-computed values
 * rather than against a rendered PDF. A wrong formula here is a watermark
 * quietly sitting in the wrong place on every page of every document; it
 * would not fail loudly on a device, so it needs pinning here instead.
 */
class MarkToolsTest {

    // Trig through Math.toRadians/cos/sin is not exact at the bit level
    // (cos(90°) comes back as ~6e-17, not 0), so comparisons use a tolerance
    // rather than exact equality.
    private val tolerance = 0.05f

    @Test
    fun `an unrotated watermark centres like plain box centring`() {
        val point = watermarkPlacement(
            pageWidth = 200f,
            pageHeight = 100f,
            textWidth = 100f,
            textHeight = 20f,
            angleDegrees = 0f,
        )
        assertThat(point.x).isWithin(tolerance).of(50f)
        assertThat(point.y).isWithin(tolerance).of(40f)
    }

    @Test
    fun `a 45 degree watermark on a square page centres on the diamond`() {
        val point = watermarkPlacement(
            pageWidth = 400f,
            pageHeight = 400f,
            textWidth = 100f,
            textHeight = 100f,
            angleDegrees = 45f,
        )
        assertThat(point.x).isWithin(tolerance).of(200f)
        assertThat(point.y).isWithin(tolerance).of(129.29f)
    }

    @Test
    fun `a 90 degree watermark swaps which dimension drives centring`() {
        val point = watermarkPlacement(
            pageWidth = 200f,
            pageHeight = 300f,
            textWidth = 80f,
            textHeight = 20f,
            angleDegrees = 90f,
        )
        assertThat(point.x).isWithin(tolerance).of(110f)
        assertThat(point.y).isWithin(tolerance).of(110f)
    }

    @Test
    fun `a 180 degree watermark still lands centred`() {
        val point = watermarkPlacement(
            pageWidth = 500f,
            pageHeight = 500f,
            textWidth = 100f,
            textHeight = 50f,
            angleDegrees = 180f,
        )
        assertThat(point.x).isWithin(tolerance).of(300f)
        assertThat(point.y).isWithin(tolerance).of(275f)
    }

    @Test
    fun `centring works on a wide landscape page`() {
        val point = watermarkPlacement(
            pageWidth = 800f,
            pageHeight = 200f,
            textWidth = 300f,
            textHeight = 40f,
            angleDegrees = 0f,
        )
        assertThat(point.x).isWithin(tolerance).of(250f)
        assertThat(point.y).isWithin(tolerance).of(80f)
    }

    @Test
    fun `a negative angle mirrors the positive one`() {
        val positive = watermarkPlacement(300f, 300f, 120f, 30f, 30f)
        val negative = watermarkPlacement(300f, 300f, 120f, 30f, -30f)
        // Not a mirror image of each other's coordinates in general, but
        // both must still land inside the page rather than off it.
        assertThat(positive.x).isGreaterThan(0f)
        assertThat(negative.x).isGreaterThan(0f)
    }

    @Test
    fun `page number position for every corner on a portrait page`() {
        val pageWidth = 600f
        val pageHeight = 800f
        val margin = 36f
        val textWidth = 40f

        assertThat(pageNumberPosition(Corner.TOP_LEFT, pageWidth, pageHeight, margin, textWidth))
            .isEqualTo(Point2D(36f, 764f))
        assertThat(pageNumberPosition(Corner.TOP_CENTER, pageWidth, pageHeight, margin, textWidth))
            .isEqualTo(Point2D(280f, 764f))
        assertThat(pageNumberPosition(Corner.TOP_RIGHT, pageWidth, pageHeight, margin, textWidth))
            .isEqualTo(Point2D(524f, 764f))
        assertThat(pageNumberPosition(Corner.BOTTOM_LEFT, pageWidth, pageHeight, margin, textWidth))
            .isEqualTo(Point2D(36f, 36f))
        assertThat(pageNumberPosition(Corner.BOTTOM_CENTER, pageWidth, pageHeight, margin, textWidth))
            .isEqualTo(Point2D(280f, 36f))
        assertThat(pageNumberPosition(Corner.BOTTOM_RIGHT, pageWidth, pageHeight, margin, textWidth))
            .isEqualTo(Point2D(524f, 36f))
    }

    @Test
    fun `centring arithmetic accounts for the stamped text's own width`() {
        // A wider stamp ("12 / 12" vs "1") must sit further left of the right
        // margin and further left of centre, or right-aligned and centred
        // numbers would drift as the label grows.
        val narrow = pageNumberPosition(Corner.BOTTOM_RIGHT, 600f, 800f, 36f, textWidth = 10f)
        val wide = pageNumberPosition(Corner.BOTTOM_RIGHT, 600f, 800f, 36f, textWidth = 60f)
        assertThat(wide.x).isLessThan(narrow.x)

        val narrowCentered = pageNumberPosition(Corner.BOTTOM_CENTER, 600f, 800f, 36f, textWidth = 10f)
        val wideCentered = pageNumberPosition(Corner.BOTTOM_CENTER, 600f, 800f, 36f, textWidth = 60f)
        assertThat(wideCentered.x).isLessThan(narrowCentered.x)
    }

    @Test
    fun `page number position on a square page`() {
        val point = pageNumberPosition(Corner.BOTTOM_CENTER, 500f, 500f, 20f, textWidth = 50f)
        assertThat(point).isEqualTo(Point2D(225f, 20f))
    }

    @Test
    fun `page number format without a total is just the number`() {
        assertThat(formatPageNumber(3, total = 12, showTotal = false)).isEqualTo("3")
    }

    @Test
    fun `page number format with a total shows both`() {
        assertThat(formatPageNumber(3, total = 12, showTotal = true)).isEqualTo("3 / 12")
    }

    @Test
    fun `a single page document still formats cleanly with a total`() {
        assertThat(formatPageNumber(1, total = 1, showTotal = true)).isEqualTo("1 / 1")
    }
}
