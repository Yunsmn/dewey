package app.dewey.ui.library

import app.dewey.work.TaskState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * One banner, two tasks that can fill it.
 *
 * The case that matters is the one this was written for: after a sort, the
 * screen showed "Shelved 114 documents" — the *indexing* result — because a
 * finished index is not Idle and the old rule preferred the index whenever it
 * was not Idle. Since indexing always precedes sorting, the sort's own summary
 * could never be shown.
 */
class BannerTaskTest {

    private val indexRunning = TaskState.Running(3, 114, "a.pdf")
    private val indexed = TaskState.Finished(processed = 114, failed = 0)
    private val sortRunning = TaskState.Running(1, 114, "b.pdf")
    private val sorted = TaskState.Sorted(moved = 96, review = 18, folders = 11, failed = 0)

    @Test
    fun `after a sort the sort's own result is shown`() {
        val banner = bannerTask(indexed, sorted, BannerSource.SORT)

        assertThat(banner).isEqualTo(sorted)
    }

    @Test
    fun `after re-indexing following a sort the index result is shown`() {
        // Adding a second folder re-indexes. That is then the newer news, even
        // though a finished sort is still sitting there.
        val banner = bannerTask(indexed, sorted, BannerSource.INDEX)

        assertThat(banner).isEqualTo(indexed)
    }

    @Test
    fun `a running task outranks any finished one`() {
        assertThat(bannerTask(indexRunning, sorted, BannerSource.SORT)).isEqualTo(indexRunning)
        assertThat(bannerTask(indexed, sortRunning, BannerSource.INDEX)).isEqualTo(sortRunning)
    }

    @Test
    fun `nothing has run yet so nothing is shown`() {
        val banner = bannerTask(TaskState.Idle, TaskState.Idle, null)

        assertThat(banner).isEqualTo(TaskState.Idle)
    }

    @Test
    fun `indexing with no sort yet shows indexing`() {
        assertThat(bannerTask(indexRunning, TaskState.Idle, null)).isEqualTo(indexRunning)
        assertThat(bannerTask(indexed, TaskState.Idle, BannerSource.INDEX)).isEqualTo(indexed)
    }

    @Test
    fun `a settled task the screen never saw settle is still shown`() {
        // After a process restart WorkManager still remembers finished jobs,
        // but nothing settled while this screen was alive, so there is no
        // ordering to go on. Showing nothing would be worse than either.
        assertThat(bannerTask(indexed, sorted, lastSettled = null)).isEqualTo(sorted)
        assertThat(bannerTask(indexed, TaskState.Idle, lastSettled = null)).isEqualTo(indexed)
    }

    @Test
    fun `a failure is news like any other`() {
        val failed = TaskState.Failed("Sort failed")

        assertThat(bannerTask(indexed, failed, BannerSource.SORT)).isEqualTo(failed)
    }
}
