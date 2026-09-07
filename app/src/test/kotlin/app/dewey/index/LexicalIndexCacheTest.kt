package app.dewey.index

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The cache exists to stop the corpus being re-tokenised once per query, so
 * what these tests assert is how many times it was built — not just that the
 * answers came back right.
 */
class LexicalIndexCacheTest {

    private var builds = 0
    private val cache = LexicalIndexCache { texts ->
        builds++
        Bm25(texts)
    }

    private fun index(count: Int, newestId: Long, vararg texts: String) =
        cache.index(count, newestId) { texts.toList() }

    @Test
    fun `an unchanged corpus is tokenised once`() {
        index(2, 7, "facture lydec", "releve attijariwafa")
        index(2, 7, "facture lydec", "releve attijariwafa")
        index(2, 7, "facture lydec", "releve attijariwafa")

        assertThat(builds).isEqualTo(1)
    }

    @Test
    fun `the same index instance comes back on a hit`() {
        val first = index(2, 7, "facture lydec", "releve attijariwafa")
        val second = index(2, 7, "facture lydec", "releve attijariwafa")

        assertThat(second).isSameInstanceAs(first)
    }

    @Test
    fun `an insert rebuilds`() {
        // A new chunk raises the newest id even when nothing was removed.
        index(2, 7, "facture lydec", "releve attijariwafa")
        index(3, 8, "facture lydec", "releve attijariwafa", "quittance de loyer")

        assertThat(builds).isEqualTo(2)
    }

    @Test
    fun `a deletion rebuilds`() {
        // Deleting the oldest chunk leaves the newest id where it was, so the
        // count is the half of the key that catches this.
        index(2, 7, "facture lydec", "releve attijariwafa")
        index(1, 7, "releve attijariwafa")

        assertThat(builds).isEqualTo(2)
    }

    @Test
    fun `a re-index that keeps the chunk count rebuilds`() {
        // Re-indexing a document deletes its chunks and inserts replacements,
        // so the count can land back where it started. The ids cannot, because
        // the database never reuses them.
        index(2, 7, "facture lydec", "releve attijariwafa")
        index(2, 9, "facture lydec revisee", "releve attijariwafa")

        assertThat(builds).isEqualTo(2)
    }

    @Test
    fun `the texts are not materialised on a hit`() {
        var materialised = 0
        val texts = { materialised++; listOf("facture lydec") }

        cache.index(1, 1, texts)
        cache.index(1, 1, texts)

        assertThat(materialised).isEqualTo(1)
    }

    @Test
    fun `an empty corpus is still cached`() {
        cache.index(0, 0) { emptyList() }
        cache.index(0, 0) { emptyList() }

        assertThat(builds).isEqualTo(1)
    }
}
