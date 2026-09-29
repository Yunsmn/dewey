package app.dewey.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [signaturePlacement]'s rotation math, checked by hand for all four values a
 * page's `/Rotate` entry can actually hold. A wrong formula here is a
 * signature that lands turned, or off the page entirely, on every rotated
 * document — quiet on a 0°-rotated preview and only visible once a real
 * rotated PDF is signed, so it is worth pinning here rather than trusting a
 * device screenshot of one rotation to stand in for all four.
 *
 * Every case below places the same normalized rectangle — near the top-left
 * of a 200×100 box — and the expected numbers are worked out by hand from
 * [signaturePlacement]'s own doc comment, corner by corner, rather than
 * derived from the code under test.
 */
class SignatureStampTest {

    private val tolerance = 0.01f

    @Test
    fun `an unrotated page keeps left-right and flips top for bottom`() {
        val rect = signaturePlacement(
            normalizedX = 0.25f,
            normalizedY = 0.25f,
            normalizedWidth = 0.5f,
            normalizedHeight = 0.5f,
            boxWidth = 200f,
            boxHeight = 100f,
            boxOriginX = 0f,
            boxOriginY = 0f,
            rotationDegrees = 0,
        )
        assertThat(rect.x).isWithin(tolerance).of(50f)
        assertThat(rect.y).isWithin(tolerance).of(25f)
        assertThat(rect.width).isWithin(tolerance).of(100f)
        assertThat(rect.height).isWithin(tolerance).of(50f)
    }

    @Test
    fun `a 90 degree page swaps which displayed axis drives width and height`() {
        val rect = signaturePlacement(
            normalizedX = 0f,
            normalizedY = 0f,
            normalizedWidth = 0.2f,
            normalizedHeight = 0.3f,
            boxWidth = 200f,
            boxHeight = 100f,
            boxOriginX = 0f,
            boxOriginY = 0f,
            rotationDegrees = 90,
        )
        assertThat(rect.x).isWithin(tolerance).of(0f)
        assertThat(rect.y).isWithin(tolerance).of(0f)
        assertThat(rect.width).isWithin(tolerance).of(60f)
        assertThat(rect.height).isWithin(tolerance).of(20f)
    }

    @Test
    fun `a 180 degree page flips both axes`() {
        val rect = signaturePlacement(
            normalizedX = 0.1f,
            normalizedY = 0.2f,
            normalizedWidth = 0.3f,
            normalizedHeight = 0.1f,
            boxWidth = 200f,
            boxHeight = 100f,
            boxOriginX = 0f,
            boxOriginY = 0f,
            rotationDegrees = 180,
        )
        assertThat(rect.x).isWithin(tolerance).of(120f)
        assertThat(rect.y).isWithin(tolerance).of(20f)
        assertThat(rect.width).isWithin(tolerance).of(60f)
        assertThat(rect.height).isWithin(tolerance).of(10f)
    }

    @Test
    fun `a 270 degree page swaps axes the other way`() {
        val rect = signaturePlacement(
            normalizedX = 0.1f,
            normalizedY = 0.2f,
            normalizedWidth = 0.3f,
            normalizedHeight = 0.1f,
            boxWidth = 200f,
            boxHeight = 100f,
            boxOriginX = 0f,
            boxOriginY = 0f,
            rotationDegrees = 270,
        )
        assertThat(rect.x).isWithin(tolerance).of(140f)
        assertThat(rect.y).isWithin(tolerance).of(60f)
        assertThat(rect.width).isWithin(tolerance).of(20f)
        assertThat(rect.height).isWithin(tolerance).of(30f)
    }

    @Test
    fun `a non-zero box origin offsets the result rather than being ignored`() {
        val rect = signaturePlacement(
            normalizedX = 0.25f,
            normalizedY = 0.25f,
            normalizedWidth = 0.5f,
            normalizedHeight = 0.5f,
            boxWidth = 200f,
            boxHeight = 100f,
            boxOriginX = 10f,
            boxOriginY = 5f,
            rotationDegrees = 0,
        )
        assertThat(rect.x).isWithin(tolerance).of(60f)
        assertThat(rect.y).isWithin(tolerance).of(30f)
    }

    @Test
    fun `a full turn and a negative rotation both normalize to the same result as zero`() {
        val base = signaturePlacement(0.25f, 0.25f, 0.5f, 0.5f, 200f, 100f, 0f, 0f, 0)
        val fullTurn = signaturePlacement(0.25f, 0.25f, 0.5f, 0.5f, 200f, 100f, 0f, 0f, 360)
        val negative = signaturePlacement(0.1f, 0.2f, 0.3f, 0.1f, 200f, 100f, 0f, 0f, -90)
        val equivalent = signaturePlacement(0.1f, 0.2f, 0.3f, 0.1f, 200f, 100f, 0f, 0f, 270)

        assertThat(fullTurn).isEqualTo(base)
        assertThat(negative.x).isWithin(tolerance).of(equivalent.x)
        assertThat(negative.y).isWithin(tolerance).of(equivalent.y)
        assertThat(negative.width).isWithin(tolerance).of(equivalent.width)
        assertThat(negative.height).isWithin(tolerance).of(equivalent.height)
    }

    @Test
    fun `the whole page rotated any way still returns a rectangle covering the whole box`() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val rect = signaturePlacement(
                normalizedX = 0f,
                normalizedY = 0f,
                normalizedWidth = 1f,
                normalizedHeight = 1f,
                boxWidth = 200f,
                boxHeight = 100f,
                boxOriginX = 0f,
                boxOriginY = 0f,
                rotationDegrees = rotation,
            )
            assertThat(rect.x).isWithin(tolerance).of(0f)
            assertThat(rect.y).isWithin(tolerance).of(0f)
            assertThat(rect.width).isWithin(tolerance).of(200f)
            assertThat(rect.height).isWithin(tolerance).of(100f)
        }
    }
}
