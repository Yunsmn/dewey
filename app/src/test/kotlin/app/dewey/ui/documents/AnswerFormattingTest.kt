package app.dewey.ui.documents

import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnswerFormattingTest {

    @Test
    fun `plain text passes through unchanged`() {
        val result = answerMarkdownToAnnotated("Your bill is due on 28 January 2023.")

        assertThat(result.text).isEqualTo("Your bill is due on 28 January 2023.")
        assertThat(result.spanStyles).isEmpty()
    }

    @Test
    fun `bold markers become a bold span without their asterisks`() {
        val result = answerMarkdownToAnnotated("Due **28 January** at the agency.")

        assertThat(result.text).isEqualTo("Due 28 January at the agency.")
        val span = result.spanStyles.single()
        assertThat(result.text.substring(span.start, span.end)).isEqualTo("28 January")
        assertThat(span.item.fontWeight).isEqualTo(FontWeight.SemiBold)
    }

    @Test
    fun `star and dash bullets become dots, and a bold label inside one still works`() {
        val result = answerMarkdownToAnnotated(
            """
            The deadline depends on the bill:

            * **January 2023 bill:** 2023-01-28
            - February 2023 bill: 2023-02-28
            """.trimIndent(),
        )

        assertThat(result.text).isEqualTo(
            "The deadline depends on the bill:\n\n• January 2023 bill: 2023-01-28\n• February 2023 bill: 2023-02-28",
        )
        val span = result.spanStyles.single()
        assertThat(result.text.substring(span.start, span.end)).isEqualTo("January 2023 bill:")
    }

    @Test
    fun `headings lose their hashes`() {
        assertThat(answerMarkdownToAnnotated("## Due dates\nJanuary").text).isEqualTo("Due dates\nJanuary")
    }

    @Test
    fun `an unmatched double star is left as written rather than eaten`() {
        assertThat(answerMarkdownToAnnotated("Total **281.26 MAD").text).isEqualTo("Total **281.26 MAD")
    }
}
