package app.dewey.classify

import app.dewey.domain.model.DocType
import app.dewey.index.VectorMath
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Built-in categories and the user's own folders, ranked together.
 *
 * This is the file that exists because of a bug. The first design asked the
 * learned categories first and fell back to the built-ins, on the reasoning
 * that a person's own filing should outrank a sentence we wrote. With one
 * learned folder there is no runner-up, so nothing could ever reject a weak
 * match, and a utility bill was filed into a folder of medical documents on a
 * real device — the built-in classifier was never consulted at all.
 */
class ScoreAllTest {

    private fun at(degrees: Double): FloatArray {
        val radians = Math.toRadians(degrees)
        return VectorMath.normalise(floatArrayOf(cos(radians).toFloat(), sin(radians).toFloat()))
    }

    private fun prototypes(vararg entries: Pair<DocType, Double>) =
        entries.map { (type, degrees) -> type to at(degrees) }

    @Test
    fun `a document nearer the user's folder than any description goes to the folder`() {
        val ranked = DocumentClassifier.scoreAll(
            vector = at(0.0),
            prototypes = prototypes(DocType.MEDICAL to 20.0),
            learned = listOf(LearnedCategory("Sante de famille", listOf(at(1.0), at(2.0)))),
        )

        assertThat(ranked.first().folderName).isEqualTo("Sante de famille")
    }

    @Test
    fun `a document nearer a description than the user's folder goes to the description`() {
        // The bill that used to be swallowed. The built-in category has to be
        // able to win, which is the whole point of ranking them together.
        val ranked = DocumentClassifier.scoreAll(
            vector = at(0.0),
            prototypes = prototypes(DocType.UTILITY_BILL to 2.0),
            learned = listOf(LearnedCategory("Sante de famille", listOf(at(40.0), at(45.0)))),
        )

        assertThat(ranked.first().folderName).isNull()
        assertThat(ranked.first().type).isEqualTo(DocType.UTILITY_BILL)
    }

    @Test
    fun `a learned category carries its folder and no type`() {
        val ranked = DocumentClassifier.scoreAll(
            vector = at(0.0),
            prototypes = emptyList(),
            learned = listOf(LearnedCategory("Voiture", listOf(at(0.0), at(1.0)))),
        )

        assertThat(ranked.single().folderName).isEqualTo("Voiture")
        assertThat(ranked.single().type).isEqualTo(DocType.UNKNOWN)
    }

    @Test
    fun `a built-in category takes its best description, not their average`() {
        // The descriptions are in different languages; averaging a French and
        // an Arabic one lands between both and matches neither.
        val ranked = DocumentClassifier.scoreAll(
            vector = at(0.0),
            prototypes = prototypes(DocType.MEDICAL to 1.0, DocType.MEDICAL to 90.0),
            learned = emptyList(),
        )

        assertThat(ranked.single().score).isGreaterThan(0.99f)
    }

    @Test
    fun `results come back best first`() {
        val ranked = DocumentClassifier.scoreAll(
            vector = at(0.0),
            prototypes = prototypes(DocType.MEDICAL to 60.0, DocType.TAX to 30.0),
            learned = listOf(LearnedCategory("Voiture", listOf(at(1.0), at(2.0)))),
        )

        assertThat(ranked.map { it.folderName ?: it.type.name })
            .containsExactly("Voiture", "TAX", "MEDICAL")
            .inOrder()
    }

    @Test
    fun `no categories at all ranks nothing`() {
        assertThat(DocumentClassifier.scoreAll(at(0.0), emptyList(), emptyList())).isEmpty()
    }
}
