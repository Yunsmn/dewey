package app.dewey.ui.documents

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.dewey.classify.DocumentClassifier
import app.dewey.domain.model.DocType
import app.dewey.domain.model.Document
import app.dewey.domain.model.TextSource
import app.dewey.ui.components.DocumentRow
import app.dewey.ui.components.IconTile
import app.dewey.ui.library.documentTitle
import app.dewey.ui.library.formatAmount
import app.dewey.ui.library.readable
import app.dewey.ui.theme.Dewey

/**
 * [documents] as lazy rows, each with a category-tinted [IconTile] leading it —
 * the one thing this list shows before a word is read, same as everywhere else
 * in the app a category appears. An `items()` call on the caller's own
 * `LazyColumn`, not a `Column` of its own, so a folder of four hundred files
 * composes only the rows on screen — the same reason `LibraryScreen` never
 * builds its sections outside a `LazyColumn`.
 */
fun LazyListScope.documentRows(documents: List<Document>, onOpenDocument: (Document) -> Unit) {
    items(documents, key = { it.id }) { document ->
        val category = document.categoryLabel(unfiled = document.docType.readable())
        DocumentRow(
            title = document.rowTitle(),
            subtitle = document.rowSubtitle(),
            filename = document.displayName,
            trailing = formatAmount(document.amount),
            onClick = { onOpenDocument(document) },
            leading = {
                IconTile(
                    icon = if (document.isBillLike()) Icons.Rounded.Receipt else Icons.Rounded.Description,
                    hue = Dewey.colors.hues.forCategory(category),
                )
            },
        )
        HorizontalDivider(color = Dewey.colors.rule, thickness = 1.dp)
    }
}

/**
 * A document waiting for a decision, listed with the reason in words rather
 * than an enum name — see `LibraryScreen`'s own version of this for the fuller
 * reasoning; this is its Documents-tab twin, since that file is not ours to
 * import a private extension from.
 */
@Composable
fun NeedsReviewRow(document: Document, onOpenDocument: (Document) -> Unit) {
    DocumentRow(
        title = null,
        subtitle = document.reviewSubtitle(),
        filename = document.displayName,
        onClick = { onOpenDocument(document) },
    )
}

private fun Document.isBillLike(): Boolean =
    docType == DocType.UTILITY_BILL || (amount != null && dueDate != null)

private fun Document.rowTitle(): String? =
    if (docType == DocType.UNKNOWN) null else documentTitle(vendor, issueDate, dueDate)

private fun Document.rowSubtitle(): String? {
    val parts = buildList {
        if (pageCount > 0) add(if (pageCount == 1) "1 page" else "$pageCount pages")
        language?.let(::add)
        if (textSource == TextSource.OCR) add("scanned")
        if (textSource == TextSource.FAILED) add("could not be read")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun Document.reviewSubtitle(): String {
    val pages = if (pageCount > 0) "$pageCount page${if (pageCount == 1) "" else "s"} · " else ""

    if (textSource == TextSource.FAILED || textSource == TextSource.NONE) {
        return pages + "nothing readable in it"
    }

    return pages + when (reviewReason) {
        DocumentClassifier.Verdict.Reason.TOO_CLOSE.name -> "could be more than one thing"
        DocumentClassifier.Verdict.Reason.NOTHING_FITS.name -> "doesn't look like anything Dewey files"
        DocumentClassifier.Verdict.Reason.NO_TEXT.name -> "nothing readable in it"
        null -> "doesn't look like anything Dewey files"
        else -> reviewReason
    }
}
