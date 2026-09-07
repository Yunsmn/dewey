package app.dewey.ui.bills

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import app.dewey.ui.components.DocumentRow
import app.dewey.ui.components.SectionHeading
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import java.time.LocalDate

@Composable
fun BillsScreen(
    viewModel: BillsViewModel,
    onOpenDocument: (Document) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BillsContent(state = state, onOpenDocument = onOpenDocument, modifier = modifier)
}

@Composable
private fun BillsContent(
    state: BillsUiState,
    onOpenDocument: (Document) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = Dewey.colors.paper,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = Dewey.spacing.gutter,
                end = Dewey.spacing.gutter,
                top = Dewey.spacing.block,
                bottom = Dewey.spacing.section,
            ),
        ) {
            item(key = "masthead") {
                Text("Bills", style = Dewey.type.Display, color = Dewey.colors.ink)
                Spacer(Modifier.height(Dewey.spacing.gutter))
            }

            if (state.isEmpty) {
                item(key = "empty") { EmptyBills() }
            }

            for (section in state.sections) {
                item(key = "heading-${section.urgency.name}") {
                    SectionHeading(label = section.urgency.label(), count = section.documents.size)
                }
                items(section.documents, key = { it.id }) { document ->
                    BillRow(
                        document = document,
                        urgency = section.urgency,
                        today = state.today,
                        onClick = { onOpenDocument(document) },
                    )
                    HorizontalDivider(color = Dewey.colors.rule, thickness = 1.dp)
                }
            }
        }
    }
}

/**
 * One bill: vendor as the title (or the filename in mono, honestly, when
 * extraction found no vendor - same fallback DocumentRow already uses),
 * amount trailing, due date as the subtitle. Only an overdue due date takes
 * the attention colour - everything else stays the usual muted ink.
 *
 * [today] comes from [BillsUiState] rather than a fresh `LocalDate.now()`
 * here, so a row's wording never disagrees with the section it was sorted
 * into - see the doc on BillsUiState.today.
 */
@Composable
private fun BillRow(document: Document, urgency: BillUrgency, today: LocalDate, onClick: () -> Unit) {
    DocumentRow(
        title = document.vendor?.trim()?.takeIf { it.isNotEmpty() },
        subtitle = document.dueDate?.let { dueDateWords(it, today) },
        filename = document.displayName,
        trailing = document.amount?.let { formatBillAmount(it, document.currency) },
        subtitleColor = if (urgency == BillUrgency.OVERDUE) Dewey.colors.attention else Dewey.colors.inkMuted,
        onClick = onClick,
    )
}

@Composable
private fun EmptyBills() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dewey.spacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Nothing due",
            style = Dewey.type.Title,
            color = Dewey.colors.ink,
        )
        Spacer(Modifier.height(Dewey.spacing.tight))
        Text(
            text = "A bill needs both an amount and a due date before Dewey can list it here.",
            style = Dewey.type.Body,
            color = Dewey.colors.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Dewey.spacing.gutter),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F2, heightDp = 780, widthDp = 390)
@Composable
private fun BillsPreview() {
    val today = LocalDate.of(2026, 9, 6)
    val documents = listOf(
        Document(
            id = 1, uri = "u1", displayName = "document (5).pdf", sizeBytes = 0, lastModified = 0,
            docType = DocType.UTILITY_BILL, vendor = "Lydec", amount = 281.26, currency = "MAD",
            dueDate = today.minusDays(3),
        ),
        Document(
            id = 2, uri = "u2", displayName = "Untitled (7).pdf", sizeBytes = 0, lastModified = 0,
            docType = DocType.UTILITY_BILL, vendor = "Amendis", amount = 145.0, currency = "MAD",
            dueDate = today.plusDays(5),
        ),
        Document(
            id = 3, uri = "u3", displayName = "IMG_8262.pdf", sizeBytes = 0, lastModified = 0,
            docType = DocType.INVOICE, vendor = null, amount = 4200.0, currency = "MAD",
            dueDate = today.plusDays(120),
        ),
    )
    DeweyTheme {
        BillsContent(
            state = BillsUiState(
                sections = listOf(
                    BillSection(BillUrgency.OVERDUE, documents.subList(0, 1)),
                    BillSection(BillUrgency.DUE_SOON, documents.subList(1, 2)),
                    BillSection(BillUrgency.LATER, documents.subList(2, 3)),
                ),
                today = today,
            ),
            onOpenDocument = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F2, heightDp = 780, widthDp = 390)
@Composable
private fun BillsEmptyPreview() {
    DeweyTheme {
        BillsContent(state = BillsUiState(), onOpenDocument = {})
    }
}
