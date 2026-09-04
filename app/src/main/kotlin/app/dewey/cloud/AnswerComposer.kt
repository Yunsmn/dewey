package app.dewey.cloud

/**
 * One chunk of a document, exactly as retrieval scored it.
 *
 * Together with the question, this is the entire request body sent to the
 * cloud model — never the document it came from, never the corpus. See
 * [AnswerPromptBuilder] for the caps that keep that true even for a
 * pathologically long document.
 */
data class RetrievedPassage(
    val documentId: Long,
    val text: String,
)

/**
 * Composes an answer to a question from passages already retrieved on device.
 *
 * The only thing that ever reaches the cloud model through this interface is
 * [RetrievedPassage.text] and the question — retrieval, embedding and
 * classification all stay on the phone regardless of whether this is wired to
 * a real model. See [GeminiAnswerComposer] for the implementation used when a
 * Firebase project is configured, and [UnconfiguredAnswerComposer] for what
 * runs when it isn't — the free tier must work perfectly without either.
 */
interface AnswerComposer {
    suspend fun answer(question: String, passages: List<RetrievedPassage>): AnswerResult
}
