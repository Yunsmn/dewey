package app.dewey.ui.documents

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

private val BULLET = Regex("""^\s*[*-]\s+""")
private val HEADING = Regex("""^\s*#{1,6}\s+""")
private val BOLD = Regex("""\*\*(.+?)\*\*""")

/**
 * The assistant's answer as styled text rather than raw Markdown.
 *
 * Gemini answers in Markdown whatever the prompt says, and the answer card
 * showed it literally — `**January 2023 bill:** 2023-01-28` with its asterisks
 * — on the emulator. Only the handful of constructs a short answer uses are
 * handled: bullets become "• ", headings lose their hashes, and `**bold**`
 * becomes bold. Anything else is left as the model wrote it, which is still
 * readable, rather than guessed at by a fuller Markdown parser this card has
 * no other use for.
 */
fun answerMarkdownToAnnotated(markdown: String): AnnotatedString = buildAnnotatedString {
    markdown.trim().lines().forEachIndexed { index, raw ->
        if (index > 0) append('\n')
        // Bullets before bold: "* **Label:** value" starts with a star and a
        // space, which a bold marker ("**") never does.
        val bullet = BULLET.find(raw)
        val line = when {
            bullet != null -> {
                append("• ")
                raw.substring(bullet.range.last + 1)
            }
            else -> raw.replace(HEADING, "")
        }
        var cursor = 0
        for (match in BOLD.findAll(line)) {
            append(line.substring(cursor, match.range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(match.groupValues[1]) }
            cursor = match.range.last + 1
        }
        append(line.substring(cursor))
    }
}
