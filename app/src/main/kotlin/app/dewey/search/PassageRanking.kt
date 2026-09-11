package app.dewey.search

import app.dewey.index.DocumentSearch

/**
 * Chunks belonging to the same document collapse to its strongest passage,
 * ranked best first.
 *
 * A top-level function rather than a private detail of any one screen:
 * `app.dewey.ui.documents.AskViewModel`'s search-as-you-type,
 * `app.dewey.assistant.hitsToPassages`'s retrieval for the cloud model, and
 * `app.dewey.assistant.DocumentAssistant`'s own source-scoring fallback all
 * need the identical collapse — a document's rank must never disagree between
 * any of them.
 */
internal fun bestPassagePerDocument(hits: List<DocumentSearch.Hit>): List<DocumentSearch.Hit> =
    hits.groupBy { it.documentId }
        .values
        .map { group -> group.maxBy { it.score } }
        .sortedByDescending { it.score }
