package app.dewey.ui.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.di.AppContainer
import app.dewey.domain.model.Document
import app.dewey.domain.model.Note
import app.dewey.ui.bills.BillsUiState
import app.dewey.ui.bills.BillsViewModel
import app.dewey.ui.bills.billsItems
import app.dewey.ui.components.NavBarClearance
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme

/**
 * Notes and bills together, one tab with two views rather than two tabs -
 * the Bills segment is [app.dewey.ui.bills.BillsViewModel] and
 * [app.dewey.ui.bills.billsItems] unchanged in substance, decorated with a
 * note count per bill so writing a note about one is never more than a tap
 * away from where the bill itself lives.
 *
 * The editor is drawn as a full-screen overlay on top of this composable
 * rather than a second navigation destination - see [NoteEditor]'s own doc.
 */
@Composable
fun NotesScreen(
    container: AppContainer,
    onOpenDocument: (Document) -> Unit,
    modifier: Modifier = Modifier,
) {
    val notesViewModel: NotesViewModel = viewModel(factory = NotesViewModel.factory(container))
    val billsViewModel: BillsViewModel = viewModel(factory = BillsViewModel.factory(container))
    val notesState by notesViewModel.state.collectAsStateWithLifecycle()
    val billsState by billsViewModel.state.collectAsStateWithLifecycle()
    var segment by rememberSaveable { mutableStateOf(NotesSegment.NOTES) }

    Box(modifier = modifier.fillMaxSize()) {
        NotesContent(
            segment = segment,
            onSegmentChange = { segment = it },
            notesState = notesState,
            billsState = billsState,
            onOpenDocument = onOpenDocument,
            onOpenNote = notesViewModel::openNote,
            onNewNote = { notesViewModel.openNewNote() },
            onAddNoteToBill = { document -> notesViewModel.openNewNote(document) },
        )

        notesState.editor?.let { editor ->
            NoteEditor(
                state = editor,
                bills = notesState.bills,
                onTitleChange = notesViewModel::updateTitle,
                onBodyChange = notesViewModel::updateBody,
                onTogglePin = notesViewModel::togglePin,
                onToggleBillPicker = { notesViewModel.setBillPickerOpen(!editor.pickingBill) },
                onPickBill = { document -> notesViewModel.attachToBill(document?.id) },
                onRequestDelete = notesViewModel::requestDelete,
                onCancelDelete = notesViewModel::cancelDelete,
                onConfirmDelete = notesViewModel::confirmDelete,
                onClose = notesViewModel::saveAndClose,
            )
        }
    }
}

@Composable
private fun NotesContent(
    segment: NotesSegment,
    onSegmentChange: (NotesSegment) -> Unit,
    notesState: NotesUiState,
    billsState: BillsUiState,
    onOpenDocument: (Document) -> Unit,
    onOpenNote: (Note) -> Unit,
    onNewNote: () -> Unit,
    onAddNoteToBill: (Document) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(containerColor = Dewey.colors.paper) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = Dewey.spacing.gutter,
                    end = Dewey.spacing.gutter,
                    top = Dewey.spacing.block,
                    bottom = if (segment == NotesSegment.NOTES) {
                        NavBarClearance + NotesFabHeight + Dewey.spacing.row
                    } else {
                        NavBarClearance
                    },
                ),
            ) {
                item(key = "masthead") {
                    Text("Notes", style = Dewey.type.Display, color = Dewey.colors.ink)
                    Spacer(Modifier.height(Dewey.spacing.gutter))
                    NotesSegmentedControl(selected = segment, onSelect = onSegmentChange)
                    Spacer(Modifier.height(Dewey.spacing.block))
                }

                when (segment) {
                    NotesSegment.NOTES -> notesItems(state = notesState, onOpenNote = onOpenNote)
                    NotesSegment.BILLS -> billsItems(
                        state = billsState,
                        onOpenDocument = onOpenDocument,
                        noteCounts = notesState.noteCountsByBillId,
                        onAddNote = onAddNoteToBill,
                    )
                }
            }
        }

        if (segment == NotesSegment.NOTES) {
            NotesFab(
                onClick = onNewNote,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Dewey.spacing.gutter, bottom = NavBarClearance),
            )
        }
    }
}

private fun LazyListScope.notesItems(state: NotesUiState, onOpenNote: (Note) -> Unit) {
    if (state.isEmpty) {
        item(key = "empty") { EmptyNotes() }
        return
    }
    items(state.notes, key = { it.id }) { note ->
        val attachedBill = note.billDocumentId?.let { id -> state.bills.firstOrNull { it.id == id } }
        NoteCard(note = note, attachedBill = attachedBill, onClick = { onOpenNote(note) })
        Spacer(Modifier.height(Dewey.spacing.tight))
    }
}

@Composable
private fun EmptyNotes() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dewey.spacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "No notes yet", style = Dewey.type.Title, color = Dewey.colors.ink)
        Spacer(Modifier.height(Dewey.spacing.tight))
        Text(
            text = "Write one about a bill, or just to yourself.",
            style = Dewey.type.Body,
            color = Dewey.colors.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Dewey.spacing.gutter),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun NotesContentPreview() {
    val bill = Document(
        id = 1, uri = "u1", displayName = "doc.pdf", sizeBytes = 0, lastModified = 0,
        vendor = "Lydec", amount = 281.26, currency = "MAD",
    )
    val notes = listOf(
        Note(id = 1, title = "Pinned reminder", body = "Renew the insurance before it lapses.", pinned = true, updatedAt = 3),
        Note(id = 2, title = "Water heater", body = "Ask about the warranty.", billDocumentId = 1, updatedAt = 2),
        Note(id = 3, title = "Groceries", body = "Milk, eggs, bread.", updatedAt = 1),
    )
    DeweyTheme {
        NotesContent(
            segment = NotesSegment.NOTES,
            onSegmentChange = {},
            notesState = NotesUiState(notes = notes, bills = listOf(bill)),
            billsState = BillsUiState(),
            onOpenDocument = {},
            onOpenNote = {},
            onNewNote = {},
            onAddNoteToBill = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F6FA, heightDp = 780, widthDp = 390)
@Composable
private fun NotesContentEmptyLightPreview() {
    DeweyTheme(dark = false) {
        NotesContent(
            segment = NotesSegment.NOTES,
            onSegmentChange = {},
            notesState = NotesUiState(),
            billsState = BillsUiState(),
            onOpenDocument = {},
            onOpenNote = {},
            onNewNote = {},
            onAddNoteToBill = {},
        )
    }
}
