package app.dewey.index

import kotlin.math.sqrt

/**
 * Vector operations for retrieval.
 *
 * Embeddings are L2-normalised once, when they are produced. Similarity is then
 * a plain dot product rather than a full cosine with two magnitude computations
 * per comparison — which matters when every query scores every chunk.
 */
object VectorMath {

    /** Normalises in place and returns the same array. */
    fun normalise(vector: FloatArray): FloatArray {
        var sumOfSquares = 0.0
        for (value in vector) sumOfSquares += value.toDouble() * value
        val magnitude = sqrt(sumOfSquares)

        // A zero vector has no direction to preserve. Leaving it alone keeps it
        // scoring zero against everything, which is the honest outcome.
        if (magnitude < EPSILON) return vector

        val scale = (1.0 / magnitude).toFloat()
        for (index in vector.indices) vector[index] = vector[index] * scale
        return vector
    }

    /** Assumes both vectors are already normalised. */
    fun dot(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Dimension mismatch: ${a.size} vs ${b.size}" }
        var total = 0f
        for (index in a.indices) total += a[index] * b[index]
        return total
    }

    private const val EPSILON = 1e-8
}
