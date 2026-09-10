package app.dewey.ui.notes

import app.dewey.domain.model.Document
import app.dewey.domain.model.Note

/**
 * State for [NotesScreen] - both segments at once, since switching between
 * Notes and Bills should never re-trigger a load.
 */
data class NotesUiState(
    val notes: List<Note> = emptyList(),
    /** Every bill, for the Bills segment and the editor's attach picker alike. */
    val bills: List<Document> = emptyList(),
    /** Null when the editor is closed. */
    val editor: NoteEditorState? = null,
) {
    val isEmpty: Boolean get() = notes.isEmpty()

    /** How many notes are attached to each bill, keyed by its document id. */
    val noteCountsByBillId: Map<Long, Int> get() = noteCountsByBill(notes)
}

/**
 * The note editor's own state, held separately from whichever [Note] it
 * started from - editing is a draft until [NotesViewModel] saves it, so a
 * screen rotation or a stray recomposition should never write half a title.
 */
data class NoteEditorState(
    /** Null while writing a note that has never been saved. */
    val noteId: Long? = null,
    val title: String = "",
    val body: String = "",
    val billDocumentId: Long? = null,
    val pinned: Boolean = false,
    /**
     * The note's original creation moment, carried through the draft so
     * saving an edit never overwrites it - see [NotesViewModel.saveAndClose].
     * Unused for a new note, which gets its own on save.
     */
    val createdAt: Long = 0,
    /** Whether the "attach to a bill" picker is expanded. */
    val pickingBill: Boolean = false,
    /** Whether the delete action is waiting on a second tap to confirm. */
    val confirmingDelete: Boolean = false,
) {
    val isNew: Boolean get() = noteId == null
}
