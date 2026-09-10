package app.dewey.data.repository

import app.dewey.data.db.NoteDao
import app.dewey.data.db.NoteRow
import app.dewey.domain.model.Note
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A stand-in for [NoteDao] that never touches a real database - a test
 * chooses exactly what rows exist, the same reasoning as
 * app.dewey.ui.scan.ScanViewModelTest's fakes.
 */
private class FakeNoteDao : NoteDao {
    private val rows = MutableStateFlow<List<NoteRow>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<NoteRow>> = rows

    override fun observeForDocument(documentId: Long): Flow<List<NoteRow>> =
        rows.map { list -> list.filter { it.billDocumentId == documentId } }

    override suspend fun upsert(row: NoteRow): Long {
        val isNew = row.id == 0L
        val id = if (isNew) nextId++ else row.id
        val saved = row.copy(id = id)
        rows.update { current ->
            if (current.any { it.id == id }) {
                current.map { if (it.id == id) saved else it }
            } else {
                current + saved
            }
        }
        return if (isNew) id else -1L
    }

    override suspend fun delete(id: Long) {
        rows.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun setPinned(id: Long, pinned: Boolean, updatedAt: Long) {
        rows.update { list -> list.map { if (it.id == id) it.copy(pinned = pinned, updatedAt = updatedAt) else it } }
    }

    fun currentRows(): List<NoteRow> = rows.value
}

class NotesRepositoryTest {

    @Test
    fun `saving a new note assigns an id and stamps both timestamps with the clock`() = runTest {
        val dao = FakeNoteDao()
        val repository = NotesRepository(dao, clock = { 1_000L })

        val saved = repository.save(Note(title = "Groceries", body = "Milk"))

        assertThat(saved.id).isEqualTo(1L)
        assertThat(saved.createdAt).isEqualTo(1_000L)
        assertThat(saved.updatedAt).isEqualTo(1_000L)
    }

    @Test
    fun `saving an edit keeps the original createdAt but bumps updatedAt`() = runTest {
        val dao = FakeNoteDao()
        var now = 1_000L
        val repository = NotesRepository(dao, clock = { now })
        val created = repository.save(Note(title = "Groceries", body = "Milk"))

        now = 2_000L
        val edited = repository.save(created.copy(body = "Milk, eggs"))

        assertThat(edited.createdAt).isEqualTo(1_000L)
        assertThat(edited.updatedAt).isEqualTo(2_000L)
        assertThat(edited.id).isEqualTo(created.id)
    }

    @Test
    fun `a standalone note has no attached bill`() = runTest {
        val dao = FakeNoteDao()
        val repository = NotesRepository(dao, clock = { 0L })

        val saved = repository.save(Note(title = "t", body = "b"))

        assertThat(saved.isStandalone).isTrue()
    }

    @Test
    fun `an attached note carries its bill's document id through a save`() = runTest {
        val dao = FakeNoteDao()
        val repository = NotesRepository(dao, clock = { 0L })

        val saved = repository.save(Note(title = "t", body = "b", billDocumentId = 42L))

        assertThat(saved.billDocumentId).isEqualTo(42L)
        assertThat(saved.isStandalone).isFalse()
    }

    @Test
    fun `delete removes the note from the dao`() = runTest {
        val dao = FakeNoteDao()
        val repository = NotesRepository(dao, clock = { 0L })
        val saved = repository.save(Note(title = "t", body = "b"))

        repository.delete(saved.id)

        assertThat(dao.currentRows()).isEmpty()
    }

    @Test
    fun `setPinned flips the flag and stamps updatedAt with the clock`() = runTest {
        val dao = FakeNoteDao()
        var now = 1_000L
        val repository = NotesRepository(dao, clock = { now })
        val saved = repository.save(Note(title = "t", body = "b"))

        now = 5_000L
        repository.setPinned(saved.id, pinned = true)

        val row = dao.currentRows().single()
        assertThat(row.pinned).isTrue()
        assertThat(row.updatedAt).isEqualTo(5_000L)
    }

    @Test
    fun `observeNotesFor only returns notes attached to that document`() = runTest {
        val dao = FakeNoteDao()
        val repository = NotesRepository(dao, clock = { 0L })
        repository.save(Note(title = "about bill 1", body = "b", billDocumentId = 1L))
        repository.save(Note(title = "about bill 2", body = "b", billDocumentId = 2L))
        repository.save(Note(title = "standalone", body = "b"))

        val titles = repository.observeNotesFor(1L).first().map { it.title }

        assertThat(titles).containsExactly("about bill 1")
    }
}
