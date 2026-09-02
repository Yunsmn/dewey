package app.dewey.index

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Bm25Test {

    private val documents = listOf(
        "Facture Lydec electricite janvier 2023 Casablanca",
        "Facture Lydec electricite fevrier 2023 Casablanca",
        "Attijariwafa Bank releve de compte mars 2024",
        "فاتورة الكهرباء لشهر نونبر 2024",
        "Decathlon Maroc recu chaussures de course",
    )
    private val bm25 = Bm25(documents)

    @Test
    fun `ranks the document containing the rare term first`() {
        val scores = bm25.scores("fevrier")

        assertThat(scores.indices.maxByOrNull { scores[it] }).isEqualTo(1)
    }

    @Test
    fun `a term in every document discriminates less than a rare one`() {
        // "Facture" appears twice, "Decathlon" once. The rarer term should carry
        // more weight, which is the whole point of the idf factor.
        val common = bm25.scores("Facture").max()
        val rare = bm25.scores("Decathlon").max()

        assertThat(rare).isGreaterThan(common)
    }

    @Test
    fun `matches Arabic terms`() {
        // Java's \\w is ASCII-only. If tokenisation were not Unicode-aware this
        // would score zero everywhere and lexical search would silently fail on
        // every Arabic document in the corpus.
        val scores = bm25.scores("فاتورة الكهرباء لشهر نونبر")

        assertThat(scores[3]).isGreaterThan(0.0)
        assertThat(scores.indices.maxByOrNull { scores[it] }).isEqualTo(3)
    }

    @Test
    fun `is case insensitive`() {
        assertThat(bm25.scores("LYDEC").max()).isGreaterThan(0.0)
    }

    @Test
    fun `scores zero for a term nothing contains`() {
        assertThat(bm25.scores("helicoptere").max()).isEqualTo(0.0)
    }

    @Test
    fun `returns zeroes for an empty query`() {
        assertThat(bm25.scores("").all { it == 0.0 }).isTrue()
        assertThat(bm25.scores("   ").all { it == 0.0 }).isTrue()
        assertThat(bm25.scores("!!!").all { it == 0.0 }).isTrue()
    }

    @Test
    fun `handles an empty corpus`() {
        val empty = Bm25(emptyList())

        assertThat(empty.size).isEqualTo(0)
        assertThat(empty.scores("anything")).isEmpty()
    }

    @Test
    fun `handles documents that are blank`() {
        val blank = Bm25(listOf("", "   ", "Facture Lydec"))

        val scores = blank.scores("Lydec")

        assertThat(scores).hasLength(3)
        assertThat(scores[2]).isGreaterThan(0.0)
    }

    @Test
    fun `returns one score per document`() {
        assertThat(bm25.scores("Lydec")).hasLength(documents.size)
    }

    @Test
    fun `survives hostile input`() {
        val nasty = listOf("\u0000", "a".repeat(50_000), "\uD800", "%%%%%%", "\n\t\r")
        for (query in nasty) {
            assertThat(bm25.scores(query)).hasLength(documents.size)
        }
    }
}

class ReciprocalRankFusionTest {

    @Test
    fun `an item ranked well in both lists beats one ranked well in either`() {
        val fused = ReciprocalRankFusion.fuse(listOf(listOf(1, 2, 3), listOf(1, 4, 5)))

        assertThat(fused.maxByOrNull { it.value }!!.key).isEqualTo(1)
    }

    @Test
    fun `rewards agreement over a single strong ranking`() {
        // 7 is second in both lists; 9 is first in one and absent from the other.
        val fused = ReciprocalRankFusion.fuse(listOf(listOf(9, 7), listOf(8, 7)))

        assertThat(fused[7]!!).isGreaterThan(fused[9]!!)
    }

    @Test
    fun `handles empty rankings`() {
        assertThat(ReciprocalRankFusion.fuse(emptyList())).isEmpty()
        assertThat(ReciprocalRankFusion.fuse(listOf(emptyList(), emptyList()))).isEmpty()
    }

    @Test
    fun `keeps items appearing in only one ranking`() {
        val fused = ReciprocalRankFusion.fuse(listOf(listOf(1, 2), listOf(3)))

        assertThat(fused.keys).containsExactly(1, 2, 3)
    }
}
