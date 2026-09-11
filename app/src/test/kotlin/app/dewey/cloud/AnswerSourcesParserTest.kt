package app.dewey.cloud

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [parseAnswerSources] is what stands between a model's free text and
 * [app.dewey.assistant.DocumentAssistant] ever showing a document as a
 * "source" — see its own KDoc for why every ambiguous case fails safe into
 * "unknown" rather than a guess.
 */
class AnswerSourcesParserTest {

    @Test
    fun `parses a simple SOURCES line and strips it from the answer`() {
        val parsed = parseAnswerSources("Renews in March.\nSOURCES: 1, 3", passageCount = 4)

        assertThat(parsed.text).isEqualTo("Renews in March.")
        assertThat(parsed.citedPassageNumbers).containsExactly(1, 3).inOrder()
    }

    @Test
    fun `SOURCES colon none means no citations, not unknown`() {
        val parsed = parseAnswerSources("Not in your documents.\nSOURCES: none", passageCount = 4)

        assertThat(parsed.text).isEqualTo("Not in your documents.")
        assertThat(parsed.citedPassageNumbers).isEmpty()
    }

    @Test
    fun `tolerates lower case, mixed case and extra whitespace around the label`() {
        val parsed = parseAnswerSources("An answer.\n  sources :   2  ", passageCount = 4)

        assertThat(parsed.citedPassageNumbers).containsExactly(2)
    }

    @Test
    fun `tolerates a trailing period`() {
        val parsed = parseAnswerSources("An answer.\nSOURCES: 1, 2.", passageCount = 4)

        assertThat(parsed.citedPassageNumbers).containsExactly(1, 2)
    }

    @Test
    fun `ignores duplicate passage numbers`() {
        val parsed = parseAnswerSources("An answer.\nSOURCES: 1, 1, 2", passageCount = 4)

        assertThat(parsed.citedPassageNumbers).containsExactly(1, 2)
    }

    @Test
    fun `ignores numbers outside the capped passage count rather than failing`() {
        val parsed = parseAnswerSources("An answer.\nSOURCES: 1, 99", passageCount = 4)

        assertThat(parsed.citedPassageNumbers).containsExactly(1)
    }

    @Test
    fun `every cited number out of range is unknown, so sources fall back rather than vanish`() {
        val parsed = parseAnswerSources("An answer.\nSOURCES: 99", passageCount = 4)

        assertThat(parsed.text).isEqualTo("An answer.")
        assertThat(parsed.citedPassageNumbers).isNull()
    }

    @Test
    fun `a missing SOURCES line is unknown, not zero sources`() {
        val parsed = parseAnswerSources("Just an answer, no final line.", passageCount = 4)

        assertThat(parsed.citedPassageNumbers).isNull()
        assertThat(parsed.text).isEqualTo("Just an answer, no final line.")
    }

    @Test
    fun `a SOURCES line that is not a number list is unparseable, not zero sources`() {
        val parsed = parseAnswerSources("An answer.\nSOURCES: the bill and the receipt", passageCount = 4)

        assertThat(parsed.citedPassageNumbers).isNull()
    }

    @Test
    fun `the displayed answer never contains the SOURCES line`() {
        val parsed = parseAnswerSources("Renews in March.\nSOURCES: 1", passageCount = 4)

        assertThat(parsed.text).doesNotContain("SOURCES")
    }

    @Test
    fun `tolerates surrounding whitespace around the whole reply`() {
        val parsed = parseAnswerSources("\n  Renews in March.\n  SOURCES: 1  \n\n", passageCount = 4)

        assertThat(parsed.text).isEqualTo("Renews in March.")
        assertThat(parsed.citedPassageNumbers).containsExactly(1)
    }
}
