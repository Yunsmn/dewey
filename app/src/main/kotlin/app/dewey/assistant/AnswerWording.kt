package app.dewey.assistant

/**
 * Phrasings an answer uses to say the documents didn't contain what was asked,
 * in English and French — the two languages the assistant has been asked in.
 *
 * Deliberately narrow. A false match hides every source chip from a real
 * answer, which is worse than a rare "not found" showing a chip, so each
 * pattern is a whole phrase rather than a bare "no" or "not".
 */
private val NOTHING_FOUND = Regex(
    listOf(
        """(?:could not|couldn't|can't|cannot|was unable to|am unable to) find""",
        """no (?:information|record|mention|document)s? (?:about|of|on|for|regarding|related)""",
        """(?:is|are) not (?:in|mentioned in|found in) (?:your|the) documents""",
        """(?:your|the) documents (?:do not|don't) (?:contain|include|mention|say|show)""",
        """(?:aucune|pas d')\s*information""",
        """(?:je )?n'ai pas trouvé""",
        """ne (?:trouve|figure) pas""",
    ).joinToString("|"),
    RegexOption.IGNORE_CASE,
)

/** Whether [answer] says the user's documents had nothing on the question. See [NOTHING_FOUND]. */
internal fun saysNothingWasFound(answer: String): Boolean = NOTHING_FOUND.containsMatchIn(answer)
