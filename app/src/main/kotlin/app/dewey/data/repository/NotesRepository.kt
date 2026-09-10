package app.dewey.data.repository

import app.dewey.data.db.NoteDao
import app.dewey.data.db.NoteRow
import app.dewey.domain.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Notes: standalone, or attached to a bill - see [Note.billDocumentId].
 *
 * [clock] is a parameter rather than a call to `System.currentTimeMillis()`
 * inline, the same reasoning as BillsUiState.today: a test can hand this a
 * fixed moment and assert on it directly.
 */
class NotesRepository(
    private val noteDao: NoteDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun observeNotes(): Flow<List<Note>> =
        noteDao.observeAll().map { rows -> sortNotes(rows.map(NoteRow::toDomain)) }

    fun observeNotesFor(documentId: Long): Flow<List<Note>> =
        noteDao.observeForDocument(documentId).map { rows -> sortNotes(rows.map(NoteRow::toDomain)) }

    /**
     * Inserts or updates [note], stamping [Note.createdAt] only the first
     * time and [Note.updatedAt] on every save.
     *
     * `id == 0L` is what says "new" - the same convention DocumentRepository
     * uses for [app.dewey.data.db.DocumentRow], since Room's autoincrement
     * primary key never assigns row id zero.
     */
    suspend fun save(note: Note): Note {
        val now = clock()
        val isNew = note.id == 0L
        val row = NoteRow(
            id = note.id,
            title = note.title,
            body = note.body,
            billDocumentId = note.billDocumentId,
            pinned = note.pinned,
            createdAt = if (isNew) now else note.createdAt,
            updatedAt = now,
        )
        val savedId = noteDao.upsert(row).let { if (it == -1L) note.id else it }
        return note.copy(id = savedId, createdAt = row.createdAt, updatedAt = row.updatedAt)
    }

    suspend fun delete(id: Long) = noteDao.delete(id)

    suspend fun setPinned(id: Long, pinned: Boolean) = noteDao.setPinned(id, pinned, clock())
}

private fun NoteRow.toDomain(): Note = Note(
    id = id,
    title = title,
    body = body,
    billDocumentId = billDocumentId,
    pinned = pinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
