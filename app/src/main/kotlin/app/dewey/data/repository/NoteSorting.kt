package app.dewey.data.repository

import app.dewey.domain.model.Note

/**
 * Pinned first, then most recently updated.
 *
 * [NoteDao][app.dewey.data.db.NoteDao]'s own queries already carry an
 * `ORDER BY` doing the same thing - this repeats it in plain Kotlin for the
 * same reason app.dewey.ui.bills.BillGrouping re-sorts bills
 * DocumentDao.observeBills already sorted: it is cheap, it means the ordering
 * itself can be asked about in a JVM test with no database involved, and a
 * screen reading from [NotesRepository] never has to trust that some other
 * caller of the DAO kept the SQL and this in agreement.
 */
internal fun sortNotes(notes: List<Note>): List<Note> =
    notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
