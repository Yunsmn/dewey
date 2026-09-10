package app.dewey.ui.documents

import app.dewey.cloud.RetrievedPassage
import app.dewey.index.DocumentSearch
import app.dewey.ui.search.bestPassagePerDocument

/**
 * The top [limit] retrieved chunks, one per document, in the shape
 * [app.dewey.cloud.AnswerComposer] expects.
 *
 * Kept a plain function — no [AskViewModel], no coroutine — so the "one per
 * document, strongest first" rule is a JVM test rather than something only
 * checked by reading an answer's sources off a device.
 */
internal fun hitsToPassages(hits: List<DocumentSearch.Hit>, limit: Int): List<RetrievedPassage> =
    bestPassagePerDocument(hits)
        .take(limit)
        .map { hit -> RetrievedPassage(hit.documentId, hit.text.trim()) }
