package app.dewey.pdf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import app.dewey.io.deleteDocumentQuietly
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Splitting one PDF into several: turning a way of dividing it up — "every N
 * pages" or explicit ranges — into page groups, then building and saving one
 * new PDF per group.
 *
 * Modeled on [PageOperations]: the grouping arithmetic below is pure, takes
 * nothing but a page count and plain text, and is fed straight from the
 * fields a person types into (see SplitOperationsTest). [buildPart] is the one
 * PDFBox-touching function pure enough to check against real in-memory
 * documents the same way PageOperationsPdfBoxTest checks merge and extract —
 * see SplitOperationsPdfBoxTest for the part page counts and order it exists
 * to pin.
 *
 * Unlike [PageOperations], the actual SAF writing lives here too, in [split]
 * below, rather than in a screen's ViewModel the way
 * [app.dewey.ui.tools.raster.PdfToImagesViewModel] writes its own pages: a
 * "part" is a whole document that has to be built and then handed off before
 * the next one starts, not a single page's already-encoded bytes, so the
 * write is as much a part of the engine's memory discipline as the build is.
 */
object SplitOperations {

    /** What was wrong with the input, so a message can be built without re-deriving it. */
    sealed interface Issue {
        /** The text had nothing usable in it — blank, or only commas/whitespace. */
        data class EmptyInput(val raw: String) : Issue

        /** "Every N pages" asked for fewer than one page per file. */
        data class InvalidPageSize(val size: Int) : Issue

        /** A comma-separated piece was neither a number nor a number-number pair. */
        data class MalformedToken(val token: String) : Issue

        /** A range written back to front, like "5-2" — unlike extracting, splitting has no use for it. */
        data class ReversedRange(val token: String) : Issue

        /** A page number outside 1..pageCount. */
        data class PageOutOfRange(val page: Int, val pageCount: Int) : Issue

        /** The same page named in two groups: it can only end up in one output file. */
        data class OverlappingPage(val page: Int) : Issue
    }

    /** One output file's pages, 0-based page-tree indices, in the order they'll appear in the part. */
    data class PageGroup(val pages: List<Int>)

    /**
     * [pageCount] pages, [size] to a file: full parts in order, followed by
     * one shorter part if [pageCount] doesn't divide evenly by [size].
     */
    fun everyNPages(pageCount: Int, size: Int): Result<List<PageGroup>> =
        if (size < 1) {
            Result.failure(SplitOperationException(Issue.InvalidPageSize(size)))
        } else {
            Result.success((0 until pageCount).chunked(size).map(::PageGroup))
        }

    /**
     * Parses text like `"1-3, 4-7, 8-11"` into one [PageGroup] per
     * comma-separated piece, each becoming its own output file.
     *
     * Unlike [PageOperations.parsePageRange], a reversed range like `"5-2"` is
     * rejected rather than walked backwards: reversing the page order *within*
     * one output file is a strange thing to ask for when the point is to
     * divide a document up, not reorder it. A page named in two groups is
     * rejected too — split into two files at once, it has no single answer
     * for which file it actually belongs in.
     */
    fun byRanges(spec: String, pageCount: Int): Result<List<PageGroup>> {
        val trimmed = spec.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(SplitOperationException(Issue.EmptyInput(spec)))
        }

        val groups = mutableListOf<PageGroup>()
        val seen = mutableSetOf<Int>()
        for (rawToken in trimmed.split(",")) {
            // Phone keyboards turn "1-3" into "1–3" on their own; an en or em
            // dash is read as the hyphen the person typed.
            val token = rawToken.trim().replace('\u2013', '-').replace('\u2014', '-')
            if (token.isEmpty()) continue

            val (start, end) = parseToken(token, pageCount).fold(
                onSuccess = { it },
                onFailure = { return Result.failure(it) },
            )
            if (start > end) {
                return Result.failure(SplitOperationException(Issue.ReversedRange(token)))
            }

            val pages = mutableListOf<Int>()
            for (page in start..end) {
                if (!seen.add(page)) {
                    return Result.failure(SplitOperationException(Issue.OverlappingPage(page)))
                }
                pages += page - 1
            }
            groups += PageGroup(pages)
        }

        // Every token was blank, e.g. spec == ",,,". Nothing malformed, but
        // nothing usable either.
        if (groups.isEmpty()) {
            return Result.failure(SplitOperationException(Issue.EmptyInput(spec)))
        }
        return Result.success(groups)
    }

    private fun parseToken(token: String, pageCount: Int): Result<Pair<Int, Int>> {
        val dash = token.indexOf('-')
        val startText = if (dash < 0) token else token.substring(0, dash).trim()
        val endText = if (dash < 0) token else token.substring(dash + 1).trim()

        val start = startText.toIntOrNull()
            ?: return Result.failure(SplitOperationException(Issue.MalformedToken(token)))
        val end = endText.toIntOrNull()
            ?: return Result.failure(SplitOperationException(Issue.MalformedToken(token)))

        if (start !in 1..pageCount) {
            return Result.failure(SplitOperationException(Issue.PageOutOfRange(start, pageCount)))
        }
        if (end !in 1..pageCount) {
            return Result.failure(SplitOperationException(Issue.PageOutOfRange(end, pageCount)))
        }
        return Result.success(start to end)
    }

    /**
     * Builds one part as a new, unsaved [PDDocument]: the pages of [group],
     * copied from [source] in order.
     *
     * Uses [PDDocument.importPage], which already appends the copy — wrapping
     * it in a second `addPage` put every page in twice the last time this
     * codebase got that wrong, in [PageOperations.merge]. The caller owns
     * closing the returned document.
     */
    fun buildPart(source: PDDocument, group: PageGroup): PDDocument {
        val part = PDDocument()
        for (index in group.pages) {
            part.importPage(source.getPage(index))
        }
        return part
    }
}

/**
 * What a split run into a folder actually produced.
 *
 * Shaped like [PageExport]: a part that fails to build or save is skipped
 * rather than failing the whole run, so a count alone can't say whether every
 * part made it — see [failedParts] for which ones (1-based) didn't.
 */
data class SplitOutcome(val parts: Int, val written: Int, val failedParts: List<Int>) {
    val isComplete: Boolean get() = failedParts.isEmpty()
}

/** Carries a [SplitOperations.Issue] through Kotlin's [Result]. */
class SplitOperationException(val issue: SplitOperations.Issue) : Exception(issue.toString())

/**
 * Splits [source] into one new PDF per [groups], written into
 * [destinationTree] — a SAF *tree* URI, converted to a document URI with
 * `buildDocumentUriUsingTree` the same way
 * [app.dewey.ui.tools.raster.PdfToImagesViewModel]'s writePage does; handing
 * the bare tree URI to `createDocument` fails with "Invalid URI".
 *
 * [nameForPart] names each file from its 1-based part number — left to the
 * caller rather than built in here, so this engine doesn't have to duplicate
 * [app.dewey.ui.tools.derivedFileName]'s stem-trimming rule, which belongs to
 * the UI layer that already owns it.
 *
 * Parts are built and saved one at a time — [SplitOperations.buildPart] then
 * [saveOpenDocument] — so a document split into dozens of parts never holds
 * more than one extra copy of itself in memory. A part that fails partway (a
 * write the resolver refuses, a page that can't be copied) is skipped and its
 * half-written file, if one was created, is deleted rather than left behind
 * as an empty PDF — see [SplitOutcome.failedParts] — and the rest of the
 * split still runs. Only a source that can't be opened at all — too large,
 * encrypted, unreadable — fails the whole call, via [PdfWorkspace.read].
 */
suspend fun split(
    workspace: PdfWorkspace,
    resolver: ContentResolver,
    source: Uri,
    sizeBytes: Long,
    destinationTree: Uri,
    groups: List<SplitOperations.PageGroup>,
    nameForPart: (partNumber: Int) -> String,
): Result<SplitOutcome> = workspace.read(source, sizeBytes) { document ->
    val parentDocument = DocumentsContract.buildDocumentUriUsingTree(
        destinationTree,
        DocumentsContract.getTreeDocumentId(destinationTree),
    )

    var written = 0
    val failed = mutableListOf<Int>()
    for ((index, group) in groups.withIndex()) {
        val partNumber = index + 1
        var created: Uri? = null
        try {
            val part = SplitOperations.buildPart(document, group)
            try {
                val name = nameForPart(partNumber)
                created = DocumentsContract.createDocument(resolver, parentDocument, PDF_MIME, name)
                    ?: throw IOException("could not create $name in $destinationTree")
                saveOpenDocument(part, resolver, created).getOrThrow()
                written++
            } finally {
                part.close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(SPLIT_TAG, "Could not save part $partNumber of $source", e)
            created?.let { resolver.deleteDocumentQuietly(it) }
            failed += partNumber
        }
    }

    SplitOutcome(parts = groups.size, written = written, failedParts = failed.toList())
}

private const val PDF_MIME = "application/pdf"
private const val SPLIT_TAG = "SplitOperations"
