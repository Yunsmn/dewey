package app.dewey.index

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Runs the real encoder on a real device.
 *
 * Everything about this component is device-specific — the native library, the
 * memory ceiling, whether a hundred-megabyte model can be mapped at all — so a
 * JVM test would prove nothing. These assertions are about behaviour rather than
 * exact values: quantised inference is not bit-reproducible across ABIs, and a
 * test demanding exact floats would fail on hardware while the app worked fine.
 */
@RunWith(AndroidJUnit4::class)
class OnnxEmbedderTest {

    companion object {
        private lateinit var embedder: OnnxEmbedder

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            embedder = OnnxEmbedder.create(context)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            embedder.close()
        }
    }

    @Test
    fun producesNormalisedVectorsOfTheRightWidth() = runBlocking {
        val vectors = embedder.embedPassages(listOf("Facture Lydec janvier 2023"))

        assertThat(vectors).hasSize(1)
        assertThat(vectors[0]).hasLength(384)

        val magnitude = kotlin.math.sqrt(vectors[0].sumOf { (it * it).toDouble() })
        assertThat(kotlin.math.abs(magnitude - 1.0)).isLessThan(1e-4)
    }

    @Test
    fun rankagesRelatedTextAboveUnrelated() = runBlocking {
        val query = embedder.embedQuery("the electricity bill from January")
        val related = embedder.embedPassages(listOf("Facture Lydec electricite janvier 2023 Casablanca"))[0]
        val unrelated = embedder.embedPassages(listOf("Attestation de travail delivree par OCP Group"))[0]

        assertThat(VectorMath.dot(query, related)).isGreaterThan(VectorMath.dot(query, unrelated))
    }

    @Test
    fun matchesAcrossLanguages() = runBlocking {
        // The whole reason for a multilingual encoder: an Arabic document must
        // be findable from a French or English question.
        val query = embedder.embedQuery("electricity bill")
        val arabic = embedder.embedPassages(listOf("فاتورة الكهرباء لشهر نونبر 2024"))[0]
        val unrelated = embedder.embedPassages(listOf("Decathlon Maroc receipt for running shoes"))[0]

        assertThat(VectorMath.dot(query, arabic)).isGreaterThan(VectorMath.dot(query, unrelated))
    }

    @Test
    fun queryAndPassagePrefixesDiffer() = runBlocking {
        // If these came back identical the prefixes would not be applied, which
        // is a silent several-point loss in retrieval accuracy.
        val asQuery = embedder.embedQuery("Lydec")
        val asPassage = embedder.embedPassages(listOf("Lydec"))[0]

        assertThat(VectorMath.dot(asQuery, asPassage)).isLessThan(0.9999f)
    }

    @Test
    fun handlesEmptyAndWhitespaceText() = runBlocking {
        val vectors = embedder.embedPassages(listOf("", "   ", "\n\t"))

        assertThat(vectors).hasSize(3)
        vectors.forEach { assertThat(it).hasLength(384) }
        // No NaNs: a NaN propagates into every similarity score and silently
        // destroys ranking rather than throwing.
        vectors.forEach { vector -> vector.forEach { assertThat(it.isNaN()).isFalse() } }
    }

    @Test
    fun handlesTextLongerThanTheContextWindow() = runBlocking {
        val long = "Facture Lydec electricite et eau potable Casablanca. ".repeat(500)

        val vector = embedder.embedPassages(listOf(long))[0]

        assertThat(vector).hasLength(384)
        vector.forEach { assertThat(it.isNaN()).isFalse() }
    }

    @Test
    fun embedsAnEmptyListWithoutFailing() = runBlocking {
        assertThat(embedder.embedPassages(emptyList())).isEmpty()
    }

    @Test
    fun batchesLargerThanTheInternalBatchSize() = runBlocking {
        val texts = (1..21).map { "Document numero $it, facture du fournisseur" }

        val vectors = embedder.embedPassages(texts)

        assertThat(vectors).hasSize(21)
        vectors.forEach { assertThat(it).hasLength(384) }
    }

    @Test
    fun reportsIndexingThroughput() = runBlocking {
        val passages = (1..16).map {
            "Facture numero $it emise par Lydec pour la consommation d'electricite et d'eau. " +
                "Montant total a payer 281.26 MAD avant le 15 du mois."
        }

        val millis = measureTimeMillis { embedder.embedPassages(passages) }

        println("EMBED_THROUGHPUT ${passages.size} passages in ${millis}ms = ${millis / passages.size}ms each")
        assertThat(millis).isGreaterThan(0)
    }
}
