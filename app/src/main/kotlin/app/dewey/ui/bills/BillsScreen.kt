package app.dewey.ui.bills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dewey.domain.model.DocType
import app.dewey.domain.model.Document
import app.dewey.ui.components.GlassCard
import app.dewey.ui.components.NavBarClearance
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
                bottom = NavBarClearance,
            ),
        ) {
            item(key = "masthead") {
                Text("Bills", style = Dewey.type.Display, color = Dewey.colors.ink)
                Spacer(Modifier.height(Dewey.spacing.gutter))
            }

            billsItems(state = state, onOpenDocument = onOpenDocument)
        }
    }
}

/**
 * The bills list's items, as a [LazyListScope] extension rather than a
 * composable of its own - a `LazyColumn` cannot host another `LazyColumn`,
 * and this is what lets [BillsContent]'s own list and the Notes tab's Bills
 * segment (see app.dewey.ui.notes.NotesScreen) share the same rows without
 * either nesting one inside the other or standing up a second `Scaffold`.
 *
 * @param noteCounts how many notes are attached to each bill, keyed by
 *   document id - empty when the caller (bare [BillsScreen]) has no notes
 *   feature wired in at all.
 * @param onAddNote shown as an "Add note" affordance on every card when set;
 *   left null to hide it entirely rather than show a control that does
 *   nothing.
 */
fun LazyListScope.billsItems(
    state: BillsUiState,
    onOpenDocument: (Document) -> Unit,
    noteCounts: Map<Long, Int> = emptyMap(),
    onAddNote: ((Document) -> Unit)? = null,
) {
    if (state.isEmpty) {
        item(key = "empty") { EmptyBills() }
    }

    for (section in state.sections) {
        item(key = "heading-${section.urgency.name}") {
            Spacer(Modifier.height(Dewey.spacing.tight))
            SectionHeading(label = section.urgency.label(), count = section.documents.size)
            Spacer(Modifier.height(Dewey.spacing.tight))
        }
        items(section.documents, key = { it.id }) { document ->
            BillCard(
                document = document,
                urgency = section.urgency,
                today = state.today,
                noteCount = noteCounts[document.id] ?: 0,
                onAddNote = onAddNote?.let { callback -> { callback(document) } },
                onClick = { onOpenDocument(document) },
            )
            Spacer(Modifier.height(Dewey.spacing.tight))
        }
    }
}

/**
 * One bill, as a card rather than a row in a list.
 *
 * A bill is not the same kind of thing as a document in the library. It has a
 * deadline attached, and money, and both are answers to a question somebody
 * came to the screen holding — so the amount is set large enough to scan down
 * a column, and the urgency is carried by a coloured edge that can be taken in
 * without reading a word.
 *
 * Red for overdue, amber for due soon, and no edge at all for later — because
 * a colour on every card is a colour that means nothing. Colour is never the
 * only signal: the section heading above says the same thing in words, and the
 * subtitle spells out "Overdue by 3 days".
 *
 * [today] comes from [BillsUiState] rather than a fresh `LocalDate.now()` here,
 * so a card's wording never disagrees with the section it was sorted into —
 * see the doc on BillsUiState.today.
 */
@Composable
private fun BillCard(
    document: Document,
    urgency: BillUrgency,
    today: LocalDate,
    onClick: () -> Unit,
    noteCount: Int = 0,
    onAddNote: (() -> Unit)? = null,
) {
    val edge = when (urgency) {
        BillUrgency.OVERDUE -> Dewey.colors.danger
        BillUrgency.DUE_SOON -> Dewey.colors.attention
        BillUrgency.LATER -> null
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        accent = edge,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.vendor?.trim()?.takeIf { it.isNotEmpty() }
                        ?: document.displayName,
                    style = Dewey.type.Title,
                    color = Dewey.colors.ink,
                    // "Lydec - Distribution Eau et Electricite" is a real vendor
                    // string and it runs to two lines at this size. Two is the
                    // most a card can give it before the amount beside it stops
                    // reading as the same row.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                document.dueDate?.let { due ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = dueDateWords(due, today),
                        style = Dewey.type.Meta,
                        color = when (urgency) {
                            BillUrgency.OVERDUE -> Dewey.colors.danger
                            BillUrgency.DUE_SOON -> Dewey.colors.attention
                            BillUrgency.LATER -> Dewey.colors.inkMuted
                        },
                    )
                }
            }

            document.amount?.let { amount ->
                Text(
                    text = formatBillAmount(amount, document.currency),
                    style = Dewey.type.Amount,
                    color = Dewey.colors.ink,
                    modifier = Modifier.padding(start = Dewey.spacing.row),
                )
            }
        }

        // The filename last and faint: it is provenance, not identity. Somebody
        // scanning for what they owe should reach the number first.
        Spacer(Modifier.height(6.dp))
        Text(
            text = document.displayName,
            style = Dewey.type.Mono,
            color = Dewey.colors.inkFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (onAddNote != null) {
            Spacer(Modifier.height(Dewey.spacing.tight))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (noteCount) {
                        0 -> "No notes yet"
                        1 -> "1 note"
                        else -> "$noteCount notes"
                    },
                    style = Dewey.type.Meta,
                    color = Dewey.colors.inkFaint,
                )
                Text(
                    text = "Add note",
                    style = Dewey.type.Meta.copy(fontWeight = FontWeight.SemiBold),
                    color = Dewey.colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(role = Role.Button, onClick = onAddNote)
                        .padding(horizontal = Dewey.spacing.tight, vertical = 4.dp),
                )
            }
        }
    }
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

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
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

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun BillsEmptyPreview() {
    DeweyTheme {
        BillsContent(state = BillsUiState(), onOpenDocument = {})
    }
}
