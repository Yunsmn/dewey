package app.dewey.widgets.notes

import app.dewey.domain.model.Note
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The Notes widget's own choice of what to show - entitlement gate, empty
 * state, and the 3-row cap. Ordering itself is
 * app.dewey.data.repository.NotesRepository.observeNotes' job (see
 * NoteSortingTest), so this suite only asserts that this function trusts the
 * order it is handed rather than re-sorting.
 */
class NotesWidgetSelectionTest {

    @Test
    fun `locked when not entitled, regardless of what notes exist`() {
        val notes = listOf(note(id = 1))

        val content = notesWidgetContent(notes, isEntitled = false)

        assertThat(content).isEqualTo(NotesWidgetContent.Locked)
    }

    @Test
    fun `empty when entitled but there are no notes`() {
        val content = notesWidgetContent(emptyList(), isEntitled = true)

        assertThat(content).isEqualTo(NotesWidgetContent.Empty)
    }

    @Test
    fun `caps at 3 rows`() {
        val notes = (1..5L).map { note(id = it) }

        val content = notesWidgetContent(notes, isEntitled = true) as NotesWidgetContent.Rows

        assertThat(content.notes).hasSize(3)
    }

    @Test
    fun `keeps the order it was handed rather than re-sorting`() {
        val notes = listOf(note(id = 3), note(id = 1), note(id = 2))

        val content = notesWidgetContent(notes, isEntitled = true) as NotesWidgetContent.Rows

        assertThat(content.notes.map { it.title }).containsExactly("t3", "t1", "t2").inOrder()
    }

    @Test
    fun `a blank title falls back to Untitled`() {
        val notes = listOf(note(id = 1, title = "   "))

        val content = notesWidgetContent(notes, isEntitled = true) as NotesWidgetContent.Rows

        assertThat(content.notes.single().title).isEqualTo("Untitled")
    }

    @Test
    fun `the preview collapses whitespace and is cut for the widget's narrower card`() {
        val body = "word ".repeat(30)
        val notes = listOf(note(id = 1, body = body))

        val content = notesWidgetContent(notes, isEntitled = true) as NotesWidgetContent.Rows

        val preview = content.notes.single().preview
        // notePreview cuts to 60 characters, trims the trailing space that
        // lands on the cut, then appends the ellipsis - 59 characters plus it.
        assertThat(preview).hasLength(60)
        assertThat(preview).endsWith("…")
    }

    private fun note(id: Long, title: String = "t$id", body: String = "b$id"): Note =
        Note(id = id, title = title, body = body)
}
