package app.dewey.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument

/**
 * Page-level edits: merge, extract, rotate, reorder, delete.
 *
 * Every function here is either pure arithmetic over page indices (testable
 * as ordinary Kotlin, no PDFBox or Android involved) or a thin PDFBox edit
 * built on top of that arithmetic. Opening and saving documents is
 * deliberately not this file's job — see [PdfWorkspace] — so every
 * PDFBox-touching function below takes an already-open [PDDocument] and
 * leaves it to the caller to read the source(s) and write the result.
 *
 * One convention runs through the whole surface: **page numbers the caller
 * passes in are 1-based**, because that is what a user sees printed on a
 * page and what they type into a text field. Everything internal —
 * PDFBox's [PDDocument.getPage], [com.tom_roush.pdfbox.pdmodel.PDPageTree] —
 * is 0-based. The conversion happens exactly once, in the parsing functions
 * below, rather than at each call site where it is easy to do twice or not
 * at all.
 */
object PageOperations {

    /** What was wrong with the input. Carries enough to build a message without re-deriving it. */
    sealed interface Issue {
        /** The range text had nothing usable in it — blank, or only commas/whitespace. */
        data class EmptyRange(val raw: String) : Issue

        /** A comma-separated piece was neither a number nor a number-number pair. */
        data class MalformedToken(val token: String) : Issue

        /** A page number outside 1..pageCount — the off-by-one this file exists to prevent. */
        data class PageOutOfRange(val page: Int, val pageCount: Int) : Issue

        /** The named pages are every page; deleting them would leave a zero-page document. */
        data class WouldEmptyDocument(val pageCount: Int) : Issue

        /** PDFBox rotation is a clockwise multiple of 90; anything else is meaningless to store. */
        data class InvalidRotation(val degrees: Int) : Issue

        /** Merging fewer than two documents is either a no-op or a caller mistake, not a merge. */
        data class TooFewDocuments(val count: Int) : Issue
    }

    // -------------------------------------------------------------------
    // Pure page-index arithmetic. No PDFBox types, no Android types — the
    // part of this file that is actually unit-tested, because it is the
    // part fed directly from a text field the user typed into.
    // -------------------------------------------------------------------

    /**
     * Parses text like `"1-3,7,9-11"`, written against a document of
     * [pageCount] pages, into 0-based page-tree indices.
     *
     * Order and duplicates from the input survive on purpose: `"5-3"` means
     * "page 5, then 4, then 3" — a user reversing a range deliberately, e.g.
     * to reverse-insert into another document — and `"1,1"` means the first
     * page twice. Silently sorting or deduping here would be a second,
     * hidden interpretation of what the user typed, imposed on every caller
     * including the ones (extract, merge) where order and repetition are
     * the point. [parsePageDeletion] below is the one caller that does not
     * want that and says so explicitly.
     *
     * Blank pieces (`",,"`, a trailing comma, stray whitespace) are
     * skipped rather than rejected — a text field will produce those from
     * completely ordinary editing, not just from someone trying to break it.
     */
    fun parsePageRange(spec: String, pageCount: Int): Result<List<Int>> {
        val trimmed = spec.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(PageOperationException(Issue.EmptyRange(spec)))
        }

        val indices = mutableListOf<Int>()
        for (rawToken in trimmed.split(",")) {
            val token = rawToken.trim()
            if (token.isEmpty()) continue

            val dash = token.indexOf('-')
            if (dash < 0) {
                val page = token.toIntOrNull()
                    ?: return Result.failure(PageOperationException(Issue.MalformedToken(token)))
                if (page !in 1..pageCount) {
                    return Result.failure(PageOperationException(Issue.PageOutOfRange(page, pageCount)))
                }
                indices += page - 1
            } else {
                val start = token.substring(0, dash).trim().toIntOrNull()
                    ?: return Result.failure(PageOperationException(Issue.MalformedToken(token)))
                val end = token.substring(dash + 1).trim().toIntOrNull()
                    ?: return Result.failure(PageOperationException(Issue.MalformedToken(token)))
                if (start !in 1..pageCount) {
                    return Result.failure(PageOperationException(Issue.PageOutOfRange(start, pageCount)))
                }
                if (end !in 1..pageCount) {
                    return Result.failure(PageOperationException(Issue.PageOutOfRange(end, pageCount)))
                }
                val walk = if (start <= end) start..end else start downTo end
                for (page in walk) indices += page - 1
            }
        }

        // Every token was blank, e.g. spec == ",,,". Nothing malformed, but
        // nothing usable either — the same outcome as an empty string, and
        // the caller shouldn't have to check for both.
        if (indices.isEmpty()) {
            return Result.failure(PageOperationException(Issue.EmptyRange(spec)))
        }

        return Result.success(indices)
    }

    /**
     * The 0-based indices to remove for a delete operation: [spec] parsed,
     * de-duplicated, and ordered ascending.
     *
     * Deliberately not [parsePageRange] directly — deletion doesn't care
     * that the user typed "3,3,1" or "3-1"; it cares which pages disappear,
     * once each. Fails with [Issue.WouldEmptyDocument] rather than quietly
     * producing a zero-page document, which PDFBox will happily let you
     * write and no PDF reader will happily open.
     */
    fun parsePageDeletion(spec: String, pageCount: Int): Result<List<Int>> =
        parsePageRange(spec, pageCount).flatMap { requested ->
            val unique = requested.toSortedSet()
            if (unique.size >= pageCount) {
                Result.failure(PageOperationException(Issue.WouldEmptyDocument(pageCount)))
            } else {
                Result.success(unique.toList())
            }
        }

    /**
     * The full resulting page order (0-based, original indices) after
     * moving the page at 1-based [from] to 1-based [to] in a [pageCount]-page
     * document.
     *
     * Returning the whole order rather than just the two changed positions
     * is deliberate: rebuilding a [com.tom_roush.pdfbox.pdmodel.PDPageTree]
     * from a full order is a simple remove-all/add-all with no index
     * arithmetic left to get wrong at the call site (see [reorder]).
     */
    fun parsePageMove(pageCount: Int, from: Int, to: Int): Result<List<Int>> {
        if (from !in 1..pageCount) {
            return Result.failure(PageOperationException(Issue.PageOutOfRange(from, pageCount)))
        }
        if (to !in 1..pageCount) {
            return Result.failure(PageOperationException(Issue.PageOutOfRange(to, pageCount)))
        }

        val order = (0 until pageCount).toMutableList()
        val moved = order.removeAt(from - 1)
        order.add(to - 1, moved)
        return Result.success(order)
    }

    /**
     * [current] rotated clockwise by [delta] degrees, wrapped into 0..359.
     *
     * Kotlin's `%` keeps the sign of the dividend, so `-90 % 360` is `-90`,
     * not `270` — a real footgun for a "rotate counter-clockwise" delta,
     * which is otherwise the obvious way to represent it. Handled once here
     * instead of at every call site.
     */
    fun normalizedRotationDegrees(current: Int, delta: Int): Int {
        val sum = (current + delta) % 360
        return if (sum < 0) sum + 360 else sum
    }

    /** A rotation delta PDFBox can store meaningfully: a non-zero multiple of 90, either direction. */
    fun validateRotationDelta(degrees: Int): Result<Unit> =
        if (degrees == 0 || degrees % 90 != 0) {
            Result.failure(PageOperationException(Issue.InvalidRotation(degrees)))
        } else {
            Result.success(Unit)
        }

    /** Merging fewer than two documents is either a no-op or a caller mistake, not a merge. */
    fun validateMergeSourceCount(count: Int): Result<Unit> =
        if (count < 2) {
            Result.failure(PageOperationException(Issue.TooFewDocuments(count)))
        } else {
            Result.success(Unit)
        }

    // -------------------------------------------------------------------
    // PDFBox-level operations. Not unit-tested here — pdfbox-android needs
    // more than a plain JVM to exercise meaningfully, and this project's
    // unit tests run without Robolectric — but kept small on purpose: each
    // one is the arithmetic above plus the minimum PDFBox calls to act on it.
    // -------------------------------------------------------------------

    /**
     * Appends every page of every document in [sources], in order, onto [into].
     *
     * Uses [PDDocument.importPage] rather than [PDDocument.addPage]: a page
     * still belongs to its source document's object graph (fonts, images,
     * the works), and `addPage` alone would leave [into] holding references
     * into a document the caller is about to close. `importPage` deep-copies
     * what the page needs and returns a page that belongs to [into].
     *
     * [Result.mapCatching], not [Result.map]: `importPage` is declared to
     * throw [java.io.IOException] on a malformed embedded resource, and
     * plain `map` does not catch — it would let that exception escape this
     * function instead of coming back as the `Result.failure` every other
     * error here does.
     */
    fun merge(sources: List<PDDocument>, into: PDDocument): Result<Unit> =
        validateMergeSourceCount(sources.size).mapCatching {
            for (source in sources) {
                for (page in source.pages) {
                    into.addPage(into.importPage(page))
                }
            }
        }

    /** Builds [into] from the pages of [source] named by [spec], in the order named. */
    fun extract(source: PDDocument, spec: String, into: PDDocument): Result<Unit> =
        parsePageRange(spec, source.numberOfPages).mapCatching { indices ->
            for (index in indices) {
                into.addPage(into.importPage(source.getPage(index)))
            }
        }

    /** Rotates the pages named by [spec] within [document] clockwise by [degrees]. */
    fun rotate(document: PDDocument, spec: String, degrees: Int): Result<Unit> =
        validateRotationDelta(degrees).flatMap {
            parsePageRange(spec, document.numberOfPages)
        }.mapCatching { indices ->
            for (index in indices) {
                val page = document.getPage(index)
                page.rotation = normalizedRotationDegrees(page.rotation, degrees)
            }
        }

    /**
     * Moves the page at 1-based [from] to 1-based [to] within [document].
     *
     * Rebuilds the page tree from scratch rather than calling
     * [com.tom_roush.pdfbox.pdmodel.PDPageTree]'s `insertBefore`/`insertAfter`
     * against a shifting index: the [PDDocument.getPage] objects are captured
     * in their new order *before* anything is removed, because a page's
     * index changes the moment an earlier page is removed but the object
     * reference does not.
     */
    fun reorder(document: PDDocument, from: Int, to: Int): Result<Unit> =
        parsePageMove(document.numberOfPages, from, to).mapCatching { newOrder ->
            val pagesInNewOrder = newOrder.map { document.getPage(it) }
            while (document.numberOfPages > 0) {
                document.removePage(0)
            }
            for (page in pagesInNewOrder) {
                document.addPage(page)
            }
        }

    /**
     * Removes the pages named by [spec] from [document].
     *
     * Walked highest index first: [PDDocument.removePage] shifts every later
     * page down by one, so removing low-to-high deletes the wrong page as
     * soon as two indices are involved — the second removal targets an index
     * that used to be the *next* page over.
     */
    fun delete(document: PDDocument, spec: String): Result<Unit> =
        parsePageDeletion(spec, document.numberOfPages).mapCatching { ascendingIndices ->
            for (index in ascendingIndices.asReversed()) {
                document.removePage(index)
            }
        }
}

/**
 * Kotlin's [Result] has no `flatMap`: [Result.map] wraps whatever [transform]
 * returns, so chaining a [transform] that itself returns a `Result` produces
 * `Result<Result<R>>` instead of flattening. Used above to chain a validation
 * step (e.g. [PageOperations.validateRotationDelta]) in front of a parse that
 * can independently fail.
 */
private inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
    fold(onSuccess = transform, onFailure = { Result.failure(it) })

/** Carries a [PageOperations.Issue] through Kotlin's [Result]. */
class PageOperationException(val issue: PageOperations.Issue) : Exception(issue.toString())
