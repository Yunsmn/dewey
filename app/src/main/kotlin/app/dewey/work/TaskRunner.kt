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

    fun observe(task: DeweyTask): Flow<TaskState> =
        workManager.getWorkInfosForUniqueWorkFlow(task.uniqueName)
            .map { infos -> infos.firstOrNull().toTaskState() }

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

    private fun WorkInfo?.toTaskState(): TaskState = when (this?.state) {
        null -> TaskState.Idle
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> TaskState.Running(0, 0, null)
        WorkInfo.State.RUNNING -> TaskState.Running(
            completed = progress.getInt(IndexWorker.KEY_COMPLETED, 0),
            total = progress.getInt(IndexWorker.KEY_TOTAL, 0),
            currentItem = progress.getString(IndexWorker.KEY_CURRENT),
        )
        WorkInfo.State.SUCCEEDED -> TaskState.Finished(
            processed = outputData.getInt(IndexWorker.KEY_PROCESSED, 0),
            failed = outputData.getInt(IndexWorker.KEY_FAILED, 0),
        )
        WorkInfo.State.FAILED -> TaskState.Failed(
            outputData.getString(IndexWorker.KEY_ERROR) ?: "Indexing failed"
        )
        WorkInfo.State.CANCELLED -> TaskState.Cancelled
    }
}
