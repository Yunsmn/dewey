package app.dewey.index

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class VectorMathTest {

    @Test
    fun `normalise gives unit length`() {
        val vector = floatArrayOf(3f, 4f)

        VectorMath.normalise(vector)

        val magnitude = sqrt(vector.sumOf { (it * it).toDouble() })
        assertThat(abs(magnitude - 1.0)).isLessThan(1e-6)
    }

    @Test
    fun `normalise preserves direction`() {
        val vector = floatArrayOf(3f, 4f)

        VectorMath.normalise(vector)

        assertThat(abs(vector[0] - 0.6f)).isLessThan(1e-6f)
        assertThat(abs(vector[1] - 0.8f)).isLessThan(1e-6f)
    }

    @Test
    fun `normalise leaves a zero vector alone`() {
        val vector = floatArrayOf(0f, 0f, 0f)

        VectorMath.normalise(vector)

        assertThat(vector.toList()).containsExactly(0f, 0f, 0f)
    }

    @Test
    fun `identical normalised vectors score one`() {
        val a = VectorMath.normalise(floatArrayOf(1f, 2f, 3f))
        val b = VectorMath.normalise(floatArrayOf(1f, 2f, 3f))

        assertThat(abs(VectorMath.dot(a, b) - 1f)).isLessThan(1e-6f)
    }

    @Test
    fun `orthogonal vectors score zero`() {
        val a = VectorMath.normalise(floatArrayOf(1f, 0f))
        val b = VectorMath.normalise(floatArrayOf(0f, 1f))

        assertThat(abs(VectorMath.dot(a, b))).isLessThan(1e-6f)
    }

    @Test
    fun `opposite vectors score minus one`() {
        val a = VectorMath.normalise(floatArrayOf(1f, 1f))
        val b = VectorMath.normalise(floatArrayOf(-1f, -1f))

        assertThat(abs(VectorMath.dot(a, b) + 1f)).isLessThan(1e-6f)
    }

    @Test
    fun `dot rejects mismatched dimensions`() {
        val failure = runCatching { VectorMath.dot(floatArrayOf(1f), floatArrayOf(1f, 2f)) }

        assertThat(failure.isFailure).isTrue()
    }
}
