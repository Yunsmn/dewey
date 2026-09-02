package app.dewey.work

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.dewey.data.db.DocumentDao
import app.dewey.sort.DocumentMover
import app.dewey.sort.UndoLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Puts everything the last sort moved back where it came from.
 *
 * Replayed backwards, so a file moved twice ends where it started. Each move is
 * dropped from the log only after it has been reversed — an undo interrupted
 * halfway must be resumable, not silently half-applied.
 */
class UndoSortWorker(
    context: Context,
    params: WorkerParameters,
    private val documentDao: DocumentDao,
    private val mover: DocumentMover,
    private val undoLog: UndoLog,
    private val notifications: TaskNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        notifications.foregroundInfo("Putting documents back", "Starting…", 0, 0)

    override suspend fun doWork(): Result {
        val batch = undoLog.latest()
            ?: return Result.success(Data.Builder().putInt(KEY_RESTORED, 0).build())

        return try {
            runCatching { setForeground(getForegroundInfo()) }

            var restored = 0
            var failed = 0
            val moves = batch.moves.reversed()

            moves.forEachIndexed { position, move ->
                coroutineContext.ensureActive()
                publish(position, moves.size, move.displayName)

                val outcome = mover.move(
                    document = Uri.parse(move.movedToUri),
                    displayName = move.displayName,
                    sourceParent = Uri.parse(move.targetParentUri),
                    targetParent = Uri.parse(move.originalParentUri),
                    folderName = "its original folder",
                )

                when (outcome) {
                    is DocumentMover.Outcome.Moved -> {
                        documentDao.byUri(move.movedToUri)?.let { row ->
                            documentDao.recordMove(row.id, outcome.to.toString(), null)
                        }
                        restored++
                    }
                    else -> {
                        Log.w(TAG, "Could not restore ${move.displayName}")
                        failed++
                    }
                }
            }

            // Only cleared once the whole batch has been walked, so a failure
            // leaves the log intact and the user can try again.
            if (failed == 0) undoLog.clear()

            publish(moves.size, moves.size, null)
            Result.success(
                Data.Builder().putInt(KEY_RESTORED, restored).putInt(KEY_FAILED, failed).build()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Undo failed", e)
            Result.failure(Data.Builder().putString(KEY_ERROR, e.message ?: "Undo failed").build())
        }
    }

    private suspend fun publish(completed: Int, total: Int, current: String?) {
        setProgress(
            Data.Builder()
                .putInt(IndexWorker.KEY_COMPLETED, completed)
                .putInt(IndexWorker.KEY_TOTAL, total)
                .putString(IndexWorker.KEY_CURRENT, current)
                .build()
        )
        runCatching {
            setForeground(
                notifications.foregroundInfo("Putting documents back", current ?: "Finishing up", completed, total)
            )
        }
    }

    companion object {
        private const val TAG = "UndoSortWorker"
        const val KEY_RESTORED = "restored"
        const val KEY_FAILED = "failed"
        const val KEY_ERROR = "error"
    }
}
