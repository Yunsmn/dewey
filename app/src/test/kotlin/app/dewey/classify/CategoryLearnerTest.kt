package app.dewey.classify

import app.dewey.index.VectorMath
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Categories learned from the folders a person already keeps.
 *
 * Vectors here are unit vectors on a circle, so a document's similarity to an
 * example is the cosine of the angle between them and the arithmetic is
 * something a reader can check by eye.
 */
class CategoryLearnerTest {

    /** A unit vector at [degrees]. Two of these are similar when they are close. */
    private fun at(degrees: Double): FloatArray {
        val radians = Math.toRadians(degrees)
        return VectorMath.normalise(floatArrayOf(cos(radians).toFloat(), sin(radians).toFloat()))
    }

    private fun folder(name: String, vararg degrees: Double) =
        LearnedCategory(name, degrees.map(::at))

    @Test
    fun `a folder with too few documents is not a category`() {
        // Two files landing somewhere is not yet a habit.
        val learned = CategoryLearner.learn(mapOf("Voiture" to listOf(at(0.0), at(2.0))))

        assertThat(learned).isEmpty()
    }

    @Test
    fun `a folder with enough documents becomes one`() {
        val learned = CategoryLearner.learn(
            mapOf("Voiture" to listOf(at(0.0), at(2.0), at(4.0)))
        )

        assertThat(learned.map { it.folderName }).containsExactly("Voiture")
    }

    @Test
    fun `a very large folder is sampled rather than held whole`() {
        val many = (1..100).map { at(it.toDouble() / 100) }

        val learned = CategoryLearner.learn(mapOf("Bills" to many))

        assertThat(learned.single().examples).hasSize(CategoryLearner.MAX_EXAMPLES)
    }

    @Test
    fun `a document scores on its closest neighbours, not the whole folder`() {
        // Two examples sit right beside the document and one is far away. The
        // far one must not drag the score down, or a folder covering two kinds
        // of document — electricity and telecom, say — would reject both.
        val category = folder("Bills", 0.0, 1.0, 90.0)

        val score = category.score(at(0.5))

        assertThat(score).isGreaterThan(0.99f)
    }

    @Test
    fun `a document scores against a folder on its closest neighbours`() {
        // A scattered folder can produce one accidental near-match. Needing two
        // is what stops a junk drawer scoring like a real category.
        val junk = folder("Misc", 0.0, 80.0, 160.0, 240.0)
        val real = folder("Bills", 10.0, 12.0, 14.0)
        val arriving = at(5.0)

        assertThat(real.score(arriving)).isGreaterThan(junk.score(arriving))
    }

    @Test
    fun `an example of the wrong width is ignored rather than fatal`() {
        // The encoder changed under a database nobody re-indexed. Costs that
        // example, not the sort.
        val category = LearnedCategory("Bills", listOf(floatArrayOf(1f, 0f, 0f), at(0.0), at(1.0)))

        assertThat(category.score(at(0.5))).isGreaterThan(0.99f)
    }

    @Test
    fun `a category with no examples scores nothing`() {
        assertThat(LearnedCategory("Empty", emptyList()).score(at(0.0))).isEqualTo(0f)
    }

    @Test
    fun `no folders at all is not an error`() {
        assertThat(CategoryLearner.learn(emptyMap())).isEmpty()
    }
}
