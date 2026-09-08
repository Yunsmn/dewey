package app.dewey.work

import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Owns the app's long-running work.
 *
 * This lives on the application, not on a screen or a ViewModel. A four-hundred
 * file sort outlives the screen that started it — the user will rotate the
 * device, background the app, and come back — and work owned by a screen either
 * dies with it or leaks past it.
 *
 * Screens observe [observe] and call [cancel]. They never hold the job.
 */
class TaskRunner(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun startIndexing(treeUri: Uri) {
        val request = OneTimeWorkRequestBuilder<IndexWorker>()
            .setInputData(Data.Builder().putString(IndexWorker.KEY_TREE_URI, treeUri.toString()).build())
            .addTag(DeweyTask.INDEX.uniqueName)
            .build()

        // KEEP, not REPLACE: asking to index while indexing is already running
        // should join the running job rather than restart it from zero. This is
        // the difference between a resilient runner and the "another task is
        // already running" error that plagues apps of this shape.
        workManager.enqueueUniqueWork(
            DeweyTask.INDEX.uniqueName,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun startSort(treeUri: Uri) {
        val request = OneTimeWorkRequestBuilder<SortWorker>()
            .setInputData(Data.Builder().putString(SortWorker.KEY_TREE_URI, treeUri.toString()).build())
            .addTag(DeweyTask.SORT.uniqueName)
            .build()
        workManager.enqueueUniqueWork(DeweyTask.SORT.uniqueName, ExistingWorkPolicy.KEEP, request)
    }

    fun startUndo() {
        val request = OneTimeWorkRequestBuilder<UndoSortWorker>()
            .addTag(DeweyTask.UNDO.uniqueName)
            .build()
        // REPLACE, unlike the others: asking to undo again means the user wants
        // it now, and a stale queued undo helps nobody.
        workManager.enqueueUniqueWork(DeweyTask.UNDO.uniqueName, ExistingWorkPolicy.REPLACE, request)
    }

    fun observe(task: DeweyTask): Flow<TaskState> =
        workManager.getWorkInfosForUniqueWorkFlow(task.uniqueName)
            .map { infos -> infos.firstOrNull().toTaskState(task) }

    /**
     * Cancels by unique name, which reaches the worker actually running.
     *
     * The worker cooperates by checking [androidx.work.CoroutineWorker]'s
     * cancellation, so this stops the loop rather than merely detaching the UI
     * from a job that keeps burning battery.
     */
    fun cancel(task: DeweyTask) {
        workManager.cancelUniqueWork(task.uniqueName)
    }
}

/**
 * Maps a work item to UI state, per the task it belongs to.
 *
 * [WorkInfo.State.SUCCEEDED] and [WorkInfo.State.FAILED] both need the task's
 * identity: the three workers write their counts and error message under
 * different keys, and reading every finished job as though it were
 * [IndexWorker] is how a completed sort ends up reporting zero.
 *
 * A top-level function rather than a private member of [TaskRunner] so it can
 * be pinned with a plain unit test against a constructed [WorkInfo], with no
 * [android.content.Context] or running [androidx.work.WorkManager] involved.
 */
internal fun WorkInfo?.toTaskState(task: DeweyTask): TaskState = when (this?.state) {
    null -> TaskState.Idle
    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> TaskState.Running(0, 0, null)
    WorkInfo.State.RUNNING -> TaskState.Running(
        completed = progress.getInt(IndexWorker.KEY_COMPLETED, 0),
        total = progress.getInt(IndexWorker.KEY_TOTAL, 0),
        currentItem = progress.getString(IndexWorker.KEY_CURRENT),
    )
    WorkInfo.State.SUCCEEDED -> when (task) {
        DeweyTask.INDEX -> TaskState.Finished(
            processed = outputData.getInt(IndexWorker.KEY_PROCESSED, 0),
            failed = outputData.getInt(IndexWorker.KEY_FAILED, 0),
        )
        DeweyTask.SORT -> TaskState.Sorted(
            moved = outputData.getInt(SortWorker.KEY_MOVED, 0),
            review = outputData.getInt(SortWorker.KEY_REVIEW, 0),
            folders = outputData.getInt(SortWorker.KEY_FOLDERS, 0),
            recognised = outputData.getInt(SortWorker.KEY_RECOGNISED, 0),
            failed = outputData.getInt(IndexWorker.KEY_FAILED, 0),
        )
        DeweyTask.UNDO -> TaskState.Restored(
            restored = outputData.getInt(UndoSortWorker.KEY_RESTORED, 0),
            failed = outputData.getInt(UndoSortWorker.KEY_FAILED, 0),
        )
    }
    WorkInfo.State.FAILED -> TaskState.Failed(
        outputData.getString(task.errorKey()) ?: task.defaultFailureMessage()
    )
    WorkInfo.State.CANCELLED -> TaskState.Cancelled
}

private fun DeweyTask.errorKey(): String = when (this) {
    DeweyTask.INDEX -> IndexWorker.KEY_ERROR
    DeweyTask.SORT -> SortWorker.KEY_ERROR
    DeweyTask.UNDO -> UndoSortWorker.KEY_ERROR
}

private fun DeweyTask.defaultFailureMessage(): String = when (this) {
    DeweyTask.INDEX -> "Indexing failed"
    DeweyTask.SORT -> "Sort failed"
    DeweyTask.UNDO -> "Undo failed"
}
