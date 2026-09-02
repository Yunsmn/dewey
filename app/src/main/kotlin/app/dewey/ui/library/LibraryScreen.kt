package app.dewey.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dewey.domain.model.DocType
import app.dewey.domain.model.Document
import app.dewey.domain.model.TextSource
import app.dewey.ui.components.DocumentRow
import app.dewey.ui.components.PrimaryAction
import app.dewey.ui.components.SecondaryAction
import app.dewey.ui.components.SectionHeading
import app.dewey.ui.components.TaskBanner
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.work.TaskState

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onSearch: () -> Unit,
    onOpenDocument: (Document) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // OpenDocumentTree is the sanctioned way to get a folder. The user picks it;
    // the app never enumerates storage it was not handed.
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(viewModel::onFolderGranted) }

    LibraryContent(
        state = state,
        onSearch = onSearch,
        onAddFolder = { pickFolder.launch(null) },
        onCancelIndexing = viewModel::onCancelIndexing,
        onOpenDocument = onOpenDocument,
        modifier = modifier,
    )
}

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onSearch: () -> Unit,
    onAddFolder: () -> Unit,
    onCancelIndexing: () -> Unit,
    onOpenDocument: (Document) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = Dewey.colors.paper,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Dewey.spacing.gutter,
                end = Dewey.spacing.gutter,
                top = Dewey.spacing.block,
                bottom = Dewey.spacing.section,
            ),
        ) {
            item(key = "masthead") {
                Masthead(state, onSearch)
                Spacer(Modifier.height(Dewey.spacing.gutter))
            }

            item(key = "task") {
                AnimatedVisibility(visible = state.task !is TaskState.Idle) {
                    Column {
                        TaskBanner(state = state.task, onCancel = onCancelIndexing)
                        Spacer(Modifier.height(Dewey.spacing.gutter))
                    }
                }
            }

            if (state.isEmpty) {
                item(key = "empty") { EmptyLibrary(onAddFolder) }
            }

            for (section in state.sections) {
                item(key = "heading-${section.type.name}") {
                    Spacer(Modifier.height(Dewey.spacing.row))
                    SectionHeading(label = section.type.readable(), count = section.documents.size)
                }
                items(section.documents, key = { it.id }) { document ->
                    DocumentRow(
                        title = document.title(),
                        subtitle = document.subtitle(),
                        filename = document.displayName,
                        onClick = { onOpenDocument(document) },
                    )
                    HorizontalDivider(color = Dewey.colors.rule, thickness = 1.dp)
                }
            }

            if (!state.isEmpty) {
                item(key = "add") {
                    Spacer(Modifier.height(Dewey.spacing.block))
                    PrimaryAction(label = "Add a folder", onClick = onAddFolder)
                }
            }
        }
    }
}

@Composable
private fun Masthead(state: LibraryUiState, onSearch: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Library", style = Dewey.type.Display, color = Dewey.colors.ink)
            // Search is only meaningful once something is shelved, so it appears
            // with the first document rather than sitting dead on an empty page.
            if (state.totalDocuments > 0) {
                SecondaryAction(label = "Find", onClick = onSearch)
            }
        }
        Spacer(Modifier.height(Dewey.spacing.tight))
        Row(horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight)) {
            Text(
                text = countLabel(state.totalDocuments, "document"),
                style = Dewey.type.Meta,
                color = Dewey.colors.inkMuted,
            )
            if (state.grantedFolders > 0) {
                Text("·", style = Dewey.type.Meta, color = Dewey.colors.inkFaint)
                Text(
                    text = countLabel(state.grantedFolders, "folder"),
                    style = Dewey.type.Meta,
                    color = Dewey.colors.inkMuted,
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onAddFolder: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dewey.spacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Nothing shelved yet",
            style = Dewey.type.Title,
            color = Dewey.colors.ink,
        )
        Spacer(Modifier.height(Dewey.spacing.tight))
        Text(
            text = "Point Dewey at a folder — Downloads is the usual suspect — " +
                "and it will read everything in it.",
            style = Dewey.type.Body,
            color = Dewey.colors.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Dewey.spacing.gutter),
        )
        Spacer(Modifier.height(Dewey.spacing.block))
        PrimaryAction(label = "Choose a folder", onClick = onAddFolder)
    }
}

private fun countLabel(count: Int, noun: String): String =
    if (count == 1) "1 $noun" else "$count ${noun}s"

/**
 * Null until the document has been classified, which is the honest answer —
 * the filename is not a title, and dressing it up as one helps nobody.
 */
private fun Document.title(): String? =
    if (docType == DocType.UNKNOWN) null else displayName.substringBeforeLast('.')

private fun Document.subtitle(): String? {
    val parts = buildList {
        if (pageCount > 0) add(if (pageCount == 1) "1 page" else "$pageCount pages")
        language?.let(::add)
        if (textSource == TextSource.OCR) add("scanned")
        if (textSource == TextSource.FAILED) add("could not be read")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun DocType.readable(): String = when (this) {
    DocType.UNKNOWN -> "Unsorted"
    DocType.UTILITY_BILL -> "Bills"
    DocType.BANK_STATEMENT -> "Bank"
    DocType.INVOICE -> "Invoices"
    DocType.RENTAL_CONTRACT -> "Contracts"
    DocType.MEDICAL -> "Medical"
    DocType.UNIVERSITY -> "University"
    DocType.INSURANCE -> "Insurance"
    DocType.EMPLOYMENT -> "Employment"
    DocType.TAX -> "Tax"
    DocType.WARRANTY -> "Warranties"
    DocType.ADMIN -> "Admin"
    DocType.TRAVEL -> "Travel"
}

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F2, heightDp = 780, widthDp = 390)
@Composable
private fun LibraryPreview() {
    val documents = listOf(
        Document(1, "u1", "document (5).pdf", 1080, 0, 1, DocType.UTILITY_BILL),
        Document(2, "u2", "Untitled (7).pdf", 1120, 0, 1, DocType.UTILITY_BILL),
        Document(3, "u3", "WhatsApp Doc 2022-01-20 at 10.22.24.pdf", 2400, 0, 2, DocType.BANK_STATEMENT),
        Document(4, "u4", "Scan_20240617_279.pdf", 900, 0, 1, DocType.INVOICE),
    )
    DeweyTheme {
        LibraryContent(
            state = LibraryUiState(
                sections = listOf(
                    LibrarySection(DocType.UTILITY_BILL, documents.take(2)),
                    LibrarySection(DocType.BANK_STATEMENT, documents.subList(2, 3)),
                    LibrarySection(DocType.INVOICE, documents.subList(3, 4)),
                ),
                totalDocuments = 4,
                grantedFolders = 1,
                task = TaskState.Running(37, 312, "Scan_20240312_004.pdf"),
            ),
            onSearch = {},
            onAddFolder = {},
            onCancelIndexing = {},
            onOpenDocument = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F2, heightDp = 780, widthDp = 390)
@Composable
private fun LibraryEmptyPreview() {
    DeweyTheme {
        LibraryContent(LibraryUiState(), {}, {}, {}, {})
    }
}
