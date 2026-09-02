package app.dewey.index

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChunkerTest {

    private val chunker = Chunker(targetSize = 100, overlap = 20)

    @Test
    fun `returns nothing for blank text`() {
        assertThat(chunker.chunk("   \n\t  ")).isEmpty()
    }

    @Test
    fun `returns a single chunk when text fits`() {
        val text = "Facture Lydec pour le mois de janvier 2023."

        val chunks = chunker.chunk(text)

        assertThat(chunks).containsExactly(text)
    }

    @Test
    fun `collapses runs of whitespace`() {
        val chunks = chunker.chunk("Montant   total \n\n  450 MAD")

        assertThat(chunks).containsExactly("Montant total 450 MAD")
    }

    @Test
    fun `splits long text into multiple chunks`() {
        val text = "mot ".repeat(200)

        val chunks = chunker.chunk(text)

        assertThat(chunks.size).isGreaterThan(1)
        assertThat(chunks.all { it.length <= 100 }).isTrue()
    }

    @Test
    fun `covers the whole input across chunks`() {
        val text = (1..80).joinToString(" ") { "terme$it" }

        val chunks = chunker.chunk(text)

        // Every token must survive somewhere, or retrieval silently loses content.
        val seen = chunks.flatMap { it.split(" ") }.toSet()
        val expected = text.split(" ").toSet()
        assertThat(seen).containsAtLeastElementsIn(expected)
    }

    @Test
    fun `consecutive chunks overlap`() {
        val text = (1..80).joinToString(" ") { "terme$it" }

        val chunks = chunker.chunk(text)

        val firstTokens = chunks[0].split(" ").toSet()
        val secondTokens = chunks[1].split(" ").toSet()
        assertThat(firstTokens.intersect(secondTokens)).isNotEmpty()
    }

    @Test
    fun `breaks on Arabic sentence punctuation`() {
        // The Arabic full stop is the only sentence end in this text. A chunker
        // that only knows ASCII punctuation would cut mid-sentence instead.
        val arabic = "هذه فاتورة الكهرباء" + "۔" + " ".repeat(1) + "المبلغ المستحق ٤٥٠ درهم" + "۔"
        val padded = arabic + " " + "كلمة ".repeat(40)

        val chunks = Chunker(targetSize = 60, overlap = 10).chunk(padded)

        assertThat(chunks).isNotEmpty()
        assertThat(chunks.first()).endsWith("۔")
    }

    @Test
    fun `terminates on text with no break opportunities`() {
        // A single unbroken token longer than the window must not loop forever.
        val chunks = chunker.chunk("x".repeat(500))

        assertThat(chunks).isNotEmpty()
        assertThat(chunks.sumOf { it.length }).isAtLeast(500 - chunks.size * 20)
    }

    @Test
    fun `rejects an overlap that would prevent progress`() {
        runCatching { Chunker(targetSize = 50, overlap = 50) }
            .also { assertThat(it.isFailure).isTrue() }
    }
}
