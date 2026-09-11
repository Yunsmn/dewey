package app.dewey.cloud

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * The caps here are the whole privacy and cost story for the cloud feature:
 * only retrieved passages and a question leave the phone, and never an
 * unbounded amount of either. That has to be provable from a fast test, not
 * from reading network traffic off a device.
 */
class AnswerPromptBuilderTest {

    private fun passage(documentId: Long, text: String) = RetrievedPassage(documentId, text)

    @Test
    fun `keeps every passage when well under every limit`() {
        val passages = listOf(passage(1, "a bill"), passage(2, "a receipt"))

        val capped = AnswerPromptBuilder.cap(passages)

        assertThat(capped).isEqualTo(passages)
    }

    @Test
    fun `drops passages past MAX_PASSAGES rather than truncating each one`() {
        val passages = (1..20).map { passage(it.toLong(), "passage $it") }

        val capped = AnswerPromptBuilder.cap(passages)

        assertThat(capped).hasSize(AnswerPromptBuilder.MAX_PASSAGES)
        assertThat(capped.map { it.documentId }).isEqualTo((1..AnswerPromptBuilder.MAX_PASSAGES).map { it.toLong() })
    }

    @Test
    fun `truncates a single passage longer than MAX_PASSAGE_CHARS`() {
        val huge = "x".repeat(AnswerPromptBuilder.MAX_PASSAGE_CHARS * 5)

        val capped = AnswerPromptBuilder.cap(listOf(passage(1, huge)))

        assertThat(capped).hasSize(1)
        assertThat(capped.first().text).hasLength(AnswerPromptBuilder.MAX_PASSAGE_CHARS)
    }

    @Test
    fun `never lets the total exceed MAX_TOTAL_CHARS, even under MAX_PASSAGES`() {
        // Four passages, each under the per-passage cap alone, but together
        // well past the total budget — the pathological-document case.
        val quarter = AnswerPromptBuilder.MAX_PASSAGE_CHARS
        val passages = (1..4).map { passage(it.toLong(), "x".repeat(quarter)) }

        val capped = AnswerPromptBuilder.cap(passages)
        val total = capped.sumOf { it.text.length }

        assertThat(total).isAtMost(AnswerPromptBuilder.MAX_TOTAL_CHARS)
    }

    @Test
    fun `stops taking passages once the total budget is spent`() {
        val fills = AnswerPromptBuilder.MAX_TOTAL_CHARS / AnswerPromptBuilder.MAX_PASSAGE_CHARS
        val passages = (1..fills + 3).map { passage(it.toLong(), "x".repeat(AnswerPromptBuilder.MAX_PASSAGE_CHARS)) }

        val capped = AnswerPromptBuilder.cap(passages)

        assertThat(capped).hasSize(fills)
    }

    @Test
    fun `drops a passage that is blank after truncation`() {
        val capped = AnswerPromptBuilder.cap(listOf(passage(1, "   "), passage(2, "real text")))

        assertThat(capped.map { it.documentId }).containsExactly(2L)
    }

    @Test
    fun `handles an empty passage list`() {
        assertThat(AnswerPromptBuilder.cap(emptyList())).isEmpty()
    }

    @Test
    fun `prompt includes the question`() {
        val prompt = AnswerPromptBuilder.build("when is the bill due", listOf(passage(1, "Lydec, due 12 March")))

        assertThat(prompt).contains("when is the bill due")
    }

    @Test
    fun `prompt includes excerpt text, labelled and in retrieval order`() {
        val prompt = AnswerPromptBuilder.build(
            "who is this from",
            listOf(passage(1, "first passage text"), passage(2, "second passage text")),
        )

        assertThat(prompt).contains("[Excerpt 1]")
        assertThat(prompt).contains("first passage text")
        assertThat(prompt).contains("[Excerpt 2]")
        assertThat(prompt).contains("second passage text")
        assertThat(prompt.indexOf("first passage text")).isLessThan(prompt.indexOf("second passage text"))
    }

    @Test
    fun `prompt says plainly when there is nothing retrieved, rather than sending nothing`() {
        val prompt = AnswerPromptBuilder.build("anything", emptyList())

        assertThat(prompt).contains("no document excerpts were found")
    }

    @Test
    fun `the prompt itself never uses the word passage, so a reply has nothing to echo`() {
        val prompt = AnswerPromptBuilder.build("anything", listOf(passage(1, "Lydec bill")))

        assertThat(prompt.lowercase()).doesNotContain("passage")
    }

    @Test
    fun `system instruction carries today's date, so due soon and overdue mean something`() {
        val instruction = AnswerPromptBuilder.systemInstruction(LocalDate.of(2026, 9, 11))

        assertThat(instruction).contains("2026-09-11")
    }

    @Test
    fun `system instruction asks for a trailing SOURCES line naming the excerpts actually used`() {
        val instruction = AnswerPromptBuilder.systemInstruction(LocalDate.of(2026, 9, 11))

        assertThat(instruction).contains("SOURCES: 1, 3")
        assertThat(instruction).contains("SOURCES: none")
    }

    @Test
    fun `system instruction carries no document content, only standing rules`() {
        val instruction = AnswerPromptBuilder.systemInstruction(LocalDate.of(2026, 9, 11))

        assertThat(instruction).doesNotContain("Lydec")
        assertThat(instruction.length).isLessThan(1_500)
    }

    @Test
    fun `prompt never exceeds the total character budget by much regardless of input size`() {
        val passages = (1..50).map { passage(it.toLong(), "x".repeat(AnswerPromptBuilder.MAX_PASSAGE_CHARS)) }

        val prompt = AnswerPromptBuilder.build("q", passages)

        // Some fixed overhead for the labels is expected; the passage content
        // itself must still be bounded.
        assertThat(prompt.length).isLessThan(AnswerPromptBuilder.MAX_TOTAL_CHARS + 2_000)
    }
}
