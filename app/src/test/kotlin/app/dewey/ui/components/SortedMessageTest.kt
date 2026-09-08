package app.dewey.ui.components

import app.dewey.work.TaskState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The sentence the demo rests on. */
class SortedMessageTest {

    @Test
    fun `a sort that filed documents says so`() {
        val message = sortedMessage(
            TaskState.Sorted(moved = 96, review = 18, folders = 11, failed = 0)
        )

        assertThat(message).isEqualTo("Filed 96 documents into 11 folders. 18 need a look.")
    }

    @Test
    fun `a rerun that recognised what was already filed does not claim nothing happened`() {
        // "Nothing new to file." on its own reads as though the sort did not
        // work, which is exactly how the already-filed bug looked to the user.
        val message = sortedMessage(
            TaskState.Sorted(moved = 0, review = 0, folders = 0, failed = 0, recognised = 96)
        )

        assertThat(message).isEqualTo("Nothing new to file; 96 documents already filed.")
    }

    @Test
    fun `a genuinely empty run still says nothing new`() {
        val message = sortedMessage(
            TaskState.Sorted(moved = 0, review = 0, folders = 0, failed = 0)
        )

        assertThat(message).isEqualTo("Nothing new to file.")
    }

    @Test
    fun `singulars read as English`() {
        val message = sortedMessage(
            TaskState.Sorted(moved = 1, review = 1, folders = 1, failed = 1)
        )

        assertThat(message).isEqualTo("Filed 1 document into 1 folder. 1 needs a look. 1 couldn't be filed.")
    }
}
