package app.dewey.cloud

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
     * The exact prompt sent to Gemini: the question, then each retained
     * passage labelled by number, with an explicit instruction to answer only
     * from them. Grounding the model in what was retrieved — rather than
     * letting it fall back on training data — is what makes "answered from
     * your documents" a true claim rather than a hopeful one.
     */
    fun build(question: String, passages: List<RetrievedPassage>): String {
        val passageBlock = cap(passages)
            .withIndex()
            .joinToString(separator = "\n\n") { (index, passage) -> "[Passage ${index + 1}]\n${passage.text}" }
            .ifEmpty { "(no passages were retrieved)" }

        return """
            Answer the question using only the passages below. Do not use outside
            knowledge. If the passages do not contain the answer, say so plainly
            instead of guessing.

            Question: $question

            $passageBlock
        """.trimIndent()
    }
}
