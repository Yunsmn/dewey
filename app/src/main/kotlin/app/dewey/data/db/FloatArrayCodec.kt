package app.dewey.data.db

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Packs embeddings as raw little-endian float32.
 *
 * Room has no native vector column, and storing 384 floats as text would roughly
 * quadruple the database and add a parse on every candidate during search. A
 * BLOB is read back with one bulk copy.
 */
object FloatArrayCodec {

    private const val BYTES_PER_FLOAT = 4

    fun encode(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * BYTES_PER_FLOAT).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(values)
        return buffer.array()
    }

    fun decode(bytes: ByteArray): FloatArray {
        require(bytes.size % BYTES_PER_FLOAT == 0) {
            "Embedding blob of ${bytes.size} bytes is not a whole number of floats"
        }
        val floats = FloatArray(bytes.size / BYTES_PER_FLOAT)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
        return floats
    }
}
