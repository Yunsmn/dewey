package app.dewey.work

import androidx.work.Data
import androidx.work.WorkInfo
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.Test

/**
 * [WorkInfo] to [TaskState], per task.
 *
 * This mapping is where a finished sort used to report zero: the old code
 * read every [WorkInfo.State.SUCCEEDED] as an indexing result, so
 * [SortWorker]'s own output keys were never looked at. Pinning it against a
 * plain constructed [WorkInfo] needs no [android.content.Context] and no
 * running [androidx.work.WorkManager] — [WorkInfo] and [Data] are ordinary
 * value classes.
 */
class TaskRunnerTest {

    private fun workInfo(state: WorkInfo.State, outputData: Data = Data.EMPTY, progress: Data = Data.EMPTY) =
        WorkInfo(
            id = UUID.randomUUID(),
            state = state,
            tags = emptySet(),
            outputData = outputData,
            progress = progress,
        )

    @Test
    fun `no work info at all is idle`() {
        assertThat(null.toTaskState(DeweyTask.INDEX)).isEqualTo(TaskState.Idle)
    }

    @Test
    fun `running reports progress regardless of task`() {
        val progress = Data.Builder()
            .putInt(IndexWorker.KEY_COMPLETED, 12)
            .putInt(IndexWorker.KEY_TOTAL, 40)
            .putString(IndexWorker.KEY_CURRENT, "Scan_004.pdf")
            .build()

        val state = workInfo(WorkInfo.State.RUNNING, progress = progress).toTaskState(DeweyTask.SORT)

        assertThat(state).isEqualTo(TaskState.Running(12, 40, "Scan_004.pdf"))
    }

    @Test
    fun `a finished index task reports processed and failed`() {
        val output = Data.Builder()
            .putInt(IndexWorker.KEY_PROCESSED, 310)
            .putInt(IndexWorker.KEY_FAILED, 2)
            .build()

        val state = workInfo(WorkInfo.State.SUCCEEDED, output).toTaskState(DeweyTask.INDEX)

        assertThat(state).isEqualTo(TaskState.Finished(processed = 310, failed = 2))
    }

    @Test
    fun `a finished sort reports moved, review, folders and failed rather than zero`() {
        // The defect this pins: reading a SORT success as though it were an
        // INDEX success finds none of these keys and reports zero across the
        // board, which is exactly what the completion banner used to show.
        val output = Data.Builder()
            .putInt(SortWorker.KEY_MOVED, 108)
            .putInt(SortWorker.KEY_REVIEW, 3)
            .putInt(SortWorker.KEY_FOLDERS, 7)
            .putInt(IndexWorker.KEY_FAILED, 1)
            .build()

        val state = workInfo(WorkInfo.State.SUCCEEDED, output).toTaskState(DeweyTask.SORT)

        assertThat(state).isEqualTo(TaskState.Sorted(moved = 108, review = 3, folders = 7, failed = 1))
    }

    @Test
    fun `a finished undo reports restored and failed`() {
        val output = Data.Builder()
            .putInt(UndoSortWorker.KEY_RESTORED, 108)
            .putInt(UndoSortWorker.KEY_FAILED, 0)
            .build()

        val state = workInfo(WorkInfo.State.SUCCEEDED, output).toTaskState(DeweyTask.UNDO)

        assertThat(state).isEqualTo(TaskState.Restored(restored = 108, failed = 0))
    }

    @Test
    fun `a failed task surfaces its own written error message`() {
        val output = Data.Builder().putString(SortWorker.KEY_ERROR, "Permission revoked mid-sort").build()

        val state = workInfo(WorkInfo.State.FAILED, output).toTaskState(DeweyTask.SORT)

        assertThat((state as TaskState.Failed).message).isEqualTo("Permission revoked mid-sort")
    }

    @Test
    fun `a failed sort with no message falls back to a sort-specific default, not indexing's`() {
        // The old fallback was hardcoded to "Indexing failed" for every task.
        val state = workInfo(WorkInfo.State.FAILED, Data.EMPTY).toTaskState(DeweyTask.SORT)

        assertThat((state as TaskState.Failed).message).isEqualTo("Sort failed")
    }

    @Test
    fun `a failed undo with no message falls back to an undo-specific default`() {
        val state = workInfo(WorkInfo.State.FAILED, Data.EMPTY).toTaskState(DeweyTask.UNDO)

        assertThat((state as TaskState.Failed).message).isEqualTo("Undo failed")
    }

    @Test
    fun `a failed index with no message falls back to the indexing default`() {
        val state = workInfo(WorkInfo.State.FAILED, Data.EMPTY).toTaskState(DeweyTask.INDEX)

        assertThat((state as TaskState.Failed).message).isEqualTo("Indexing failed")
    }

    @Test
    fun `cancelled maps to cancelled regardless of task`() {
        assertThat(workInfo(WorkInfo.State.CANCELLED).toTaskState(DeweyTask.UNDO)).isEqualTo(TaskState.Cancelled)
    }

    @Test
    fun `idle and running are not terminal`() {
        assertThat(TaskState.Idle.isTerminal).isFalse()
        assertThat(TaskState.Running(1, 10, null).isTerminal).isFalse()
    }

    @Test
    fun `every settled state is terminal`() {
        // LibraryViewModel re-reads the undo log only once the sort task
        // settles; a state wrongly left out of this list is a state that
        // never gets that re-read.
        assertThat(TaskState.Sorted(moved = 1, review = 0, folders = 1, failed = 0).isTerminal).isTrue()
        assertThat(TaskState.Restored(restored = 1, failed = 0).isTerminal).isTrue()
        assertThat(TaskState.Finished(1, 0).isTerminal).isTrue()
        assertThat(TaskState.Failed("oops").isTerminal).isTrue()
        assertThat(TaskState.Cancelled.isTerminal).isTrue()
    }
}
