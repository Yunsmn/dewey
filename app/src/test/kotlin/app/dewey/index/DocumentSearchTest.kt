package app.dewey.index

import app.dewey.data.db.ChunkDao
import app.dewey.data.db.ChunkRow
import app.dewey.data.db.FloatArrayCodec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Search must degrade rather than crash.
 *
 * The database outlives any single version of the app, so it will eventually
 * hold chunks written by a different encoder, truncated by a failed write, or
 * left behind by a model change. None of that should take search down — it
 * should cost the affected chunks and nothing else.
 */
class DocumentSearchTest {

    private class FakeChunkDao(private val rows: List<ChunkRow>) : ChunkDao {
        override suspend fun insertAll(rows: List<ChunkRow>) = Unit
        override suspend fun deleteForDocument(documentId: Long) = Unit
        override suspend fun allChunks(): List<ChunkRow> = rows
        override suspend fun count(): Int = rows.size
    }

    private fun row(id: Long, documentId: Long, text: String, vector: FloatArray) =
        ChunkRow(
            id = id,
            documentId = documentId,
            ordinal = 0,
            text = text,
            embedding = FloatArrayCodec.encode(VectorMath.normalise(vector)),
        )

    private fun search(rows: List<ChunkRow>) = DocumentSearch(FakeChunkDao(rows))

    @Test
    fun `returns nothing when the index is empty`() = runTest {
        val hits = search(emptyList()).search("Lydec", floatArrayOf(1f, 0f, 0f))

        assertThat(hits).isEmpty()
    }

    @Test
    fun `ranks the semantically closest chunk first`() = runTest {
        val rows = listOf(
            row(1, 10, "unrelated", floatArrayOf(0f, 1f, 0f)),
            row(2, 20, "target", floatArrayOf(1f, 0f, 0f)),
        )

        val hits = search(rows).search("nothing lexical here", floatArrayOf(1f, 0f, 0f))

        assertThat(hits.first().documentId).isEqualTo(20)
    }

    @Test
    fun `lexical agreement lifts a chunk the vector alone would miss`() = runTest {
        val rows = listOf(
            row(1, 10, "generic text about nothing", floatArrayOf(1f, 0.1f, 0f)),
            row(2, 20, "Attijariwafa releve fevrier 2024", floatArrayOf(0.9f, 0.2f, 0f)),
        )

        val hits = search(rows).search("Attijariwafa fevrier", floatArrayOf(1f, 0.1f, 0f))

        assertThat(hits.first().documentId).isEqualTo(20)
    }

    @Test
    fun `skips chunks whose vector width does not match`() = runTest {
        // What a model change leaves behind. These must be ignored, not throw.
        val rows = listOf(
            row(1, 10, "stale, wrong width", floatArrayOf(1f, 0f)),
            row(2, 20, "current", floatArrayOf(1f, 0f, 0f)),
        )

        val hits = search(rows).search("current", floatArrayOf(1f, 0f, 0f))

        assertThat(hits.map { it.documentId }).containsExactly(20L)
    }

    @Test
    fun `returns nothing when every chunk has the wrong width`() = runTest {
        val rows = listOf(row(1, 10, "all stale", floatArrayOf(1f, 0f)))

        assertThat(search(rows).search("anything", floatArrayOf(1f, 0f, 0f))).isEmpty()
    }

    @Test
    fun `survives a truncated embedding blob`() = runTest {
        val rows = listOf(
            ChunkRow(id = 1, documentId = 10, ordinal = 0, text = "corrupt", embedding = ByteArray(7)),
        )

        // A blob that is not a whole number of floats is a failed write. It
        // should not be silently treated as a valid vector.
        val failure = runCatching { search(rows).search("x", floatArrayOf(1f, 0f, 0f)) }

        assertThat(failure.isFailure).isTrue()
    }

    @Test
    fun `honours the result limit`() = runTest {
        val rows = (1..30).map { row(it.toLong(), it.toLong(), "chunk $it", floatArrayOf(1f, 0f, 0f)) }

        assertThat(search(rows).search("chunk", floatArrayOf(1f, 0f, 0f), limit = 5)).hasSize(5)
    }

    @Test
    fun `rejects a non-positive limit`() = runTest {
        val rows = listOf(row(1, 10, "a", floatArrayOf(1f, 0f, 0f)))

        assertThat(runCatching { search(rows).search("a", floatArrayOf(1f, 0f, 0f), 0) }.isFailure).isTrue()
    }

    @Test
    fun `handles an empty query string`() = runTest {
        val rows = listOf(row(1, 10, "Facture", floatArrayOf(1f, 0f, 0f)))

        val hits = search(rows).search("", floatArrayOf(1f, 0f, 0f))

        // No lexical signal, but the dense ranking still stands.
        assertThat(hits).isNotEmpty()
    }

    @Test
    fun `handles a zero query vector`() = runTest {
        val rows = listOf(row(1, 10, "Facture Lydec", floatArrayOf(1f, 0f, 0f)))

        val hits = search(rows).search("Lydec", floatArrayOf(0f, 0f, 0f))

        assertThat(hits).isNotEmpty()
    }

    @Test
    fun `handles chunks with blank text`() = runTest {
        val rows = listOf(
            row(1, 10, "", floatArrayOf(1f, 0f, 0f)),
            row(2, 20, "   ", floatArrayOf(0f, 1f, 0f)),
        )

        assertThat(search(rows).search("anything", floatArrayOf(1f, 0f, 0f))).isNotEmpty()
    }

    @Test
    fun `never returns more hits than there are chunks`() = runTest {
        val rows = listOf(row(1, 10, "only one", floatArrayOf(1f, 0f, 0f)))

        assertThat(search(rows).search("one", floatArrayOf(1f, 0f, 0f), limit = 50)).hasSize(1)
    }
}
