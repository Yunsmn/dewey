package app.dewey.ui.documents

import app.dewey.cloud.RetrievedPassage
import app.dewey.index.DocumentSearch
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [hitsToPassages] is what stands between raw retrieval and the cloud model —
 * see its own KDoc for why that boundary is worth a test independent of any
 * database or network call.
 */
class AskPassagesTest {

    private fun hit(documentId: Long, text: String, score: Double) =
        DocumentSearch.Hit(documentId = documentId, chunkId = 0, text = text, score = score)

    @Test
    fun `several chunks of the same document collapse to its strongest one`() {
        val hits = listOf(
            hit(1, "weak passage", score = 0.2),
            hit(1, "strong passage", score = 0.9),
            hit(2, "only passage", score = 0.5),
        )

        val passages = hitsToPassages(hits, limit = 10)

        assertThat(passages).containsExactly(
            RetrievedPassage(1, "strong passage"),
            RetrievedPassage(2, "only passage"),
        )
    }

    @Test
    fun `passages come back strongest document first`() {
        val hits = listOf(
            hit(1, "first", score = 0.3),
            hit(2, "second", score = 0.9),
        )

        val passages = hitsToPassages(hits, limit = 10)

        assertThat(passages.map { it.documentId }).containsExactly(2L, 1L).inOrder()
    }

    @Test
    fun `limit caps how many documents are handed to the cloud model`() {
        val hits = (1..20L).map { hit(it, "text $it", score = it.toDouble()) }

        val passages = hitsToPassages(hits, limit = 8)

        assertThat(passages).hasSize(8)
    }

    @Test
    fun `passage text is trimmed`() {
        val hits = listOf(hit(1, "  padded text  ", score = 1.0))

        val passages = hitsToPassages(hits, limit = 10)

        assertThat(passages.single().text).isEqualTo("padded text")
    }

    @Test
    fun `no hits at all is no passages, not an error`() {
        assertThat(hitsToPassages(emptyList(), limit = 10)).isEmpty()
    }
}
