package app.dewey.widgets.notes

import app.dewey.domain.model.Note
import app.dewey.ui.notes.notePreview

/**
 * What the Notes widget draws, decided independently of Glance so it can be
 * unit-tested on the JVM without an AppWidgetManager - see
 * [app.dewey.widgets.bills.BillsWidgetContent] for the matching shape on the
 * other paid widget.
 */
sealed interface NotesWidgetContent {
    data object Locked : NotesWidgetContent
    data object Empty : NotesWidgetContent
    data class Rows(val notes: List<NoteWidgetRow>) : NotesWidgetContent
}

/** One row's worth of a note: its title and a one-line taste of its body. */
data class NoteWidgetRow(val title: String, val preview: String)

/** How many rows the widget ever shows at once. */
private const val MAX_NOTE_ROWS = 3

/** How much of a note's body the widget's narrower card has room for, versus the full screen's 120. */
private const val WIDGET_PREVIEW_LENGTH = 60

/**
 * Picks the notes the widget shows: up to [MAX_NOTE_ROWS], in whatever order
 * [notes] already arrives in.
 *
 * [app.dewey.data.repository.NotesRepository.observeNotes] already sorts
 * pinned-first-then-most-recently-updated (see `sortNotes` in
 * app.dewey.data.repository.NoteSorting.kt), so this function does not
 * re-derive that rule - only the entitlement gate and the cap.
 */
fun notesWidgetContent(notes: List<Note>, isEntitled: Boolean): NotesWidgetContent {
    if (!isEntitled) return NotesWidgetContent.Locked
    if (notes.isEmpty()) return NotesWidgetContent.Empty

    val rows = notes.take(MAX_NOTE_ROWS).map { note ->
        NoteWidgetRow(
            title = note.title.trim().ifEmpty { "Untitled" },
            preview = notePreview(note.body, WIDGET_PREVIEW_LENGTH),
        )
    }
    return NotesWidgetContent.Rows(rows)
}
