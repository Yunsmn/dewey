package app.dewey.data.db

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FloatArrayCodecTest {

    @Test
    fun `round trips values exactly`() {
        val original = floatArrayOf(0f, 1f, -1f, 0.5f, -0.333333f, Float.MAX_VALUE, Float.MIN_VALUE)

        val restored = FloatArrayCodec.decode(FloatArrayCodec.encode(original))

        // Exact, not approximate: this is a byte-level copy, so any drift means
        // the encoding is wrong rather than imprecise.
        assertThat(restored.toList()).isEqualTo(original.toList())
    }

    @Test
    fun `round trips an empty array`() {
        val restored = FloatArrayCodec.decode(FloatArrayCodec.encode(floatArrayOf()))

        assertThat(restored).isEmpty()
    }

    @Test
    fun `uses four bytes per float`() {
        assertThat(FloatArrayCodec.encode(FloatArray(384))).hasLength(384 * 4)
    }

    @Test
    fun `rejects a blob that is not a whole number of floats`() {
        val failure = runCatching { FloatArrayCodec.decode(ByteArray(7)) }

        assertThat(failure.isFailure).isTrue()
    }
}
