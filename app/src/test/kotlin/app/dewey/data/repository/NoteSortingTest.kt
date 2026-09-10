package app.dewey.data.repository

import app.dewey.domain.model.Note
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [sortNotes]'s ordering rule - pinned first, then most recently updated -
 * see the function's own doc for why this is asked about directly rather
 * than only through the DAO's `ORDER BY`.
 */
class NoteSortingTest {

    @Test
    fun `an unpinned note never sorts above a pinned one`() {
        val pinned = note(id = 1, pinned = true, updatedAt = 1)
        val unpinned = note(id = 2, pinned = false, updatedAt = 100)

        val sorted = sortNotes(listOf(unpinned, pinned))

        assertThat(sorted.map { it.id }).containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `within the same pinned state, the most recently updated comes first`() {
        val oldest = note(id = 1, updatedAt = 1)
        val newest = note(id = 2, updatedAt = 3)
        val middle = note(id = 3, updatedAt = 2)

        val sorted = sortNotes(listOf(oldest, newest, middle))

        assertThat(sorted.map { it.id }).containsExactly(2L, 3L, 1L).inOrder()
    }

    @Test
    fun `multiple pinned notes are still ordered by recency among themselves`() {
        val pinnedOld = note(id = 1, pinned = true, updatedAt = 1)
        val pinnedNew = note(id = 2, pinned = true, updatedAt = 2)
        val unpinned = note(id = 3, pinned = false, updatedAt = 99)

        val sorted = sortNotes(listOf(pinnedOld, unpinned, pinnedNew))

        assertThat(sorted.map { it.id }).containsExactly(2L, 1L, 3L).inOrder()
    }

    @Test
    fun `an empty list sorts to an empty list`() {
        assertThat(sortNotes(emptyList())).isEmpty()
    }

    private fun note(id: Long, pinned: Boolean = false, updatedAt: Long = 0): Note =
        Note(id = id, title = "t$id", body = "b$id", pinned = pinned, updatedAt = updatedAt)
}
