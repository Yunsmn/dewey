package app.dewey.cloud

/**
 * The trailing `SOURCES:` line pulled back out of a model reply, plus the
 * answer text with that line removed.
 *
 * [citedPassageNumbers] being `null` means the line was missing, its content
 * could not be read as a list of numbers, or every number named a passage the
 * model was never shown — genuinely unknown, which is why
 * [app.dewey.assistant.DocumentAssistant] falls back to scoring rather than
 * treating it as zero sources. An empty list only ever means the explicit
 * "SOURCES: none".
 */
internal data class ParsedAnswer(
    val text: String,
    val citedPassageNumbers: List<Int>?,
)

/** Case-insensitive by design — see [parseAnswerSources]'s own KDoc. */
private val SOURCES_LINE = Regex("""sources\s*:\s*(.*)""", RegexOption.IGNORE_CASE)

/**
 * Strips the final `SOURCES:` line [AnswerPromptBuilder.build] asks the model
 * to end every reply with, and turns its content into passage numbers.
 *
 * Pure and unit-tested on its own because it is the one place a model's free
 * text becomes something [app.dewey.assistant.DocumentAssistant] can trust
 * enough to decide what a person sees as "sources" — a model that gets the
 * format slightly wrong must fail safe into "unknown", never into a guess.
 *
 * Tolerated without treating the line as missing: surrounding whitespace, a
 * trailing period, "Sources" in any case, numbers past [passageCount]
 * (silently dropped — the model cannot know [AnswerPromptBuilder.cap] trimmed
 * the list it was shown), and duplicates.
 *
 * [passageCount] is deliberately the size of the *capped* passage list, not
 * however many were retrieved — a number is only "in range" if the model
 * could actually have seen a passage with that label.
 */
internal fun parseAnswerSources(rawText: String, passageCount: Int): ParsedAnswer {
    val trimmed = rawText.trim()
    val lines = trimmed.lines()
    val lastLine = lines.last().trim()

    val match = SOURCES_LINE.matchEntire(lastLine) ?: return ParsedAnswer(text = trimmed, citedPassageNumbers = null)

    val body = match.groupValues[1].trim().removeSuffix(".").trim()
    val strippedText = lines.dropLast(1).joinToString("\n").trim()

    if (body.equals("none", ignoreCase = true)) {
        return ParsedAnswer(text = strippedText, citedPassageNumbers = emptyList())
    }

    val tokens = body.split(",").map { it.trim() }
    val numbers = tokens.map { it.toIntOrNull() }
    if (tokens.isEmpty() || numbers.any { it == null }) {
        // Not a comma-separated list of numbers and not "none" either — the
        // model did not follow the format, so this is unparseable, not zero
        // sources.
        return ParsedAnswer(text = strippedText, citedPassageNumbers = null)
    }

    val validNumbers = numbers.filterNotNull().filter { it in 1..passageCount }.distinct()
    // Numbers were given but none of them name a passage the model was shown:
    // it answered from something and mislabelled it. That is an unknown, not
    // "nothing cited" — only an explicit "none" hides every source.
    if (validNumbers.isEmpty()) return ParsedAnswer(text = strippedText, citedPassageNumbers = null)
    return ParsedAnswer(text = strippedText, citedPassageNumbers = validNumbers)
}
