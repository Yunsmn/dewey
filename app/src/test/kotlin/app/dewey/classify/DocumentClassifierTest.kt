package app.dewey.classify

import app.dewey.index.Embedder
import app.dewey.index.VectorMath
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The classifier's decision logic, isolated from the encoder.
 *
 * Whether the embeddings are any good is measured in
 * tools/eval/classify_bench.py against a labelled corpus, which a unit test
 * cannot do. What a unit test can pin down is the part that decides between
 * answering and abstaining - and that is the part which, if wrong, either files
 * documents confidently in the wrong folder or sends everything to review.
 */
class DocumentClassifierTest {

    /**
     * Scores every prototype identically, so only the thresholds decide.
     *
     * [documentAxis] lets a document sit away from all of them: with the same
     * vector on both sides the similarity is exactly 1.0 and the "nothing fits"
     * branch is unreachable, which is a property of the fake rather than of the
     * classifier.
     */
    private fun flatEmbedder(documentAxis: Boolean = false) = object : Embedder {
        override val dimensions = 8
        override suspend fun embedPassages(texts: List<String>) =
            texts.map { text ->
                val isPrototype = CategoryPrototypes.all.any { text.contains(it.second) }
                val vector = FloatArray(dimensions) { 0.35f }
                if (documentAxis && !isPrototype) {
                    // Point it somewhere the prototypes are not.
                    vector.fill(0f)
                    vector[dimensions - 1] = 1f
                }
                VectorMath.normalise(vector)
            }
        override suspend fun embedQuery(text: String) = embedPassages(listOf(text)).first()
    }

    /** Gives each category its own axis, so one can genuinely win. */
    private fun axisEmbedder() = object : Embedder {
        private val types = CategoryPrototypes.byType.keys.toList()
        override val dimensions = 32

        private fun axis(text: String): Int {
            CategoryPrototypes.all.forEach { (type, prototype) ->
                if (text.contains(prototype)) return types.indexOf(type)
            }
            types.forEachIndexed { index, type -> if (text.contains(type.name)) return index }
            return -1
        }

        override suspend fun embedPassages(texts: List<String>): List<FloatArray> =
            texts.map { text ->
                val vector = FloatArray(dimensions) { 0.08f }
                val a = axis(text)
                if (a >= 0) vector[a % dimensions] = 1f
                VectorMath.normalise(vector)
            }

        override suspend fun embedQuery(text: String) = embedPassages(listOf(text)).first()
    }

    @Test
    fun `abstains on empty text`() = runTest {
        val verdict = DocumentClassifier(axisEmbedder()).classify("   ")

        assertThat((verdict as DocumentClassifier.Verdict.Unsure).reason)
            .isEqualTo(DocumentClassifier.Verdict.Reason.NO_TEXT)
    }

    @Test
    fun `abstains when nothing is similar enough`() = runTest {
        // Every category scores the same and low: the research-paper case.
        val classifier = DocumentClassifier(flatEmbedder(documentAxis = true))

        val verdict = classifier.classify("a paper about transformers")

        assertThat((verdict as DocumentClassifier.Verdict.Unsure).reason)
            .isEqualTo(DocumentClassifier.Verdict.Reason.NOTHING_FITS)
    }

    @Test
    fun `abstains when two categories fit equally`() = runTest {
        val classifier = DocumentClassifier(
            flatEmbedder(), minimumSimilarity = 0f, minimumMargin = 0.01f,
        )

        val verdict = classifier.classify("something ambiguous")

        assertThat((verdict as DocumentClassifier.Verdict.Unsure).reason)
            .isEqualTo(DocumentClassifier.Verdict.Reason.TOO_CLOSE)
    }

    @Test
    fun `commits when one category clearly wins`() = runTest {
        val classifier = DocumentClassifier(axisEmbedder(), minimumSimilarity = 0f, minimumMargin = 0f)

        val verdict = classifier.classify("UTILITY_BILL document body")

        assertThat(verdict).isInstanceOf(DocumentClassifier.Verdict.Confident::class.java)
        assertThat((verdict as DocumentClassifier.Verdict.Confident).margin).isGreaterThan(0f)
    }

    @Test
    fun `embeds the prototypes only once across many documents`() = runTest {
        var calls = 0
        val counting = object : Embedder {
            override val dimensions = 8
            override suspend fun embedPassages(texts: List<String>): List<FloatArray> {
                calls++
                return texts.map { VectorMath.normalise(FloatArray(8) { 0.35f }) }
            }
            override suspend fun embedQuery(text: String) = embedPassages(listOf(text)).first()
        }
        val classifier = DocumentClassifier(counting, minimumSimilarity = 0f, minimumMargin = 0f)

        repeat(5) { classifier.classify("document $it") }

        // One batch for the prototypes, then one per document. Re-embedding 35
        // prototypes per file would dominate a four-hundred-file sort.
        assertThat(calls).isEqualTo(6)
    }

    @Test
    fun `survives hostile document text`() = runTest {
        val classifier = DocumentClassifier(axisEmbedder())

        for (text in listOf(" ", "\uD800", "x".repeat(50_000), "!!!", "\n\t\r")) {
            classifier.classify(text)
        }
    }

    @Test
    fun `every category is described in Arabic as well`() {
        // The measured jump from 98% to 100% came from closing exactly this gap:
        // the only misclassified documents were Arabic contracts, in the one
        // category that had no Arabic description. A category losing one again
        // is a regression worth failing on.
        val arabic = '؀'..'ۿ'
        for ((_, texts) in CategoryPrototypes.byType) {
            assertThat(texts.size).isAtLeast(3)
            assertThat(texts.any { text -> text.any { it in arabic } }).isTrue()
        }
    }

    @Test
    fun `describes every category several times over`() {
        // A type with no prototype can never be predicted, so its folder would
        // sit empty forever while its documents pile up in review.
        assertThat(CategoryPrototypes.byType).isNotEmpty()
        assertThat(CategoryPrototypes.all.size).isAtLeast(CategoryPrototypes.byType.size * 3)
    }
}
