package app.dewey.search

import app.dewey.domain.model.Document

/**
 * One document that matched, and the passage that made it match.
 *
 * Carrying the snippet is not decoration. When someone searches for a document
 * they cannot name, the passage is the evidence that the right file came back —
 * without it the result is a filename they already could not recognise.
 */
data class SearchResult(
    val document: Document,
    val snippet: String,
    val score: Double,
)

/**
 * What the search screen can be showing.
 *
 * Distinguishes "we have not looked yet" from "we looked and found nothing",
 * because those need very different things on screen and collapsing them into
 * an empty list produces the classic wrong empty state.
 */
sealed interface SearchUiState {

    data object Idle : SearchUiState

    /** The encoder is unpacking on first use — over a hundred megabytes of it. */
    data object Preparing : SearchUiState

    data object Searching : SearchUiState

    data class Results(val query: String, val results: List<SearchResult>) : SearchUiState {
        val isEmpty: Boolean get() = results.isEmpty()
    }

    data class Failed(val message: String) : SearchUiState
}
