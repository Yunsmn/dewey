package app.dewey.cloud

import java.time.LocalDate

/**
 * Builds the text sent to the cloud model, and is the one place that decides
 * what "only the retrieved passages" actually means in bytes.
 *
 * Kept pure and separate from [GeminiAnswerComposer] deliberately: the caps
 * here are the whole privacy and cost story for this feature, so they need to
 * be checkable by a fast unit test instead of by reading network traffic off
 * a device. Nothing in this file touches the SDK.
 */
object AnswerPromptBuilder {

    /** More passages than this is retrieval noise, not signal. */
    const val MAX_PASSAGES = 8

    /** A single chunk this long is already far more than a model needs to answer from. */
    const val MAX_PASSAGE_CHARS = 2_000

    /**
     * Hard ceiling on the whole request body, independent of passage count. A
     * document that produced one enormous chunk should not be able to send more
     * than a document that produced eight reasonably sized ones.
     */
    const val MAX_TOTAL_CHARS = 8_000

    /**
     * Applies [MAX_PASSAGES], [MAX_PASSAGE_CHARS] and [MAX_TOTAL_CHARS], in that
     * order. Passages are taken in the order retrieval ranked them — dropping
     * from the tail rather than truncating each one equally, since a hit that
     * scored higher is more likely to actually contain the answer.
     */
    fun cap(passages: List<RetrievedPassage>): List<RetrievedPassage> {
        var remaining = MAX_TOTAL_CHARS
        val capped = ArrayList<RetrievedPassage>(minOf(passages.size, MAX_PASSAGES))

        for (passage in passages.take(MAX_PASSAGES)) {
            if (remaining <= 0) break
            val text = passage.text.take(minOf(MAX_PASSAGE_CHARS, remaining))
            if (text.isBlank()) continue
            capped += passage.copy(text = text)
            remaining -= text.length
        }

        return capped
    }

    /**
     * The model's standing instructions, sent as a system instruction rather
     * than inside the question.
     *
     * Every line here was measured against the live model on 2026-09-11 with
     * the question "Which bills are due soon?" over three 2023 electricity
     * bills and a medical certificate. The earlier prompt, instructions and
     * all in the user turn, got "The provided passages do not contain the
     * answer" twice, with the word "passages" leaking into the reply. This
     * version answered both times that all three bills were already overdue,
     * with their dates and amounts, cited exactly those three, and never said
     * "excerpt". What made the difference:
     *
     *  - **Today's date.** "Due soon" and "overdue" mean nothing to a model
     *    that does not know when now is.
     *  - **Saying what the documents do show** when nothing matches exactly,
     *    instead of a flat "not found".
     *  - **Calling them "your documents"** and the labels "excerpts", so there
     *    is no prompt vocabulary for the reply to echo.
     *
     * The `SOURCES:` line is how [app.dewey.assistant.DocumentAssistant] learns
     * which documents an answer relied on — see [parseAnswerSources].
     */
    fun systemInstruction(today: LocalDate): String = """
        You are Dewey, an assistant that answers questions about the user's own documents.
        Today's date is $today.
        Use only the document excerpts you are given, never outside knowledge.
        Speak to the user about "your documents" or "your bills"; never mention excerpts, excerpt numbers, passages or "the provided text".
        If nothing matches the question exactly, say what the documents do show that is relevant, including dates and amounts, and say plainly when something is already overdue. Only if nothing at all is relevant, say you could not find it in their documents.
        Keep answers short: a sentence or a short list.
        End your reply with one final line and nothing after it, naming the excerpts you used, exactly like:
        SOURCES: 1, 3
        or, if you used none:
        SOURCES: none
    """.trimIndent()

    /**
     * The request itself: the question, then each retained passage labelled by
     * number in retrieval order. Grounding the model in what was retrieved —
     * rather than letting it fall back on training data — is what makes
     * "answered from your documents" a true claim rather than a hopeful one.
     *
     * Numbering always matches [cap]'s output, since the model only ever sees
     * the capped list; [GeminiAnswerComposer] resolves the `SOURCES:` numbers
     * against that same list.
     */
    fun build(question: String, passages: List<RetrievedPassage>): String {
        val excerptBlock = cap(passages)
            .withIndex()
            .joinToString(separator = "\n\n") { (index, passage) -> "[Excerpt ${index + 1}]\n${passage.text}" }
            .ifEmpty { "(no document excerpts were found)" }

        return "Question: $question\n\nDocument excerpts:\n\n$excerptBlock"
    }
}
