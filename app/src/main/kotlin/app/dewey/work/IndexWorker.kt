package app.dewey.work

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.dewey.data.repository.DocumentRepository
import app.dewey.data.storage.SafDocumentSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Imports and indexes every PDF under a granted folder.
 *
 * The expensive part is per-document: text extraction, possibly OCR, embedding.
 * Progress is published after each one so a four-hundred-file run reads as
 * movement rather than a frozen bar.
 */
class IndexWorker(
    context: Context,
    params: WorkerParameters,
    private val source: SafDocumentSource,
    private val repository: DocumentRepository,
    private val notifications: TaskNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        notifications.foregroundInfo("Indexing documents", "Starting…", 0, 0)

    override suspend fun doWork(): Result {
        val treeUri = inputData.getString(KEY_TREE_URI)?.let(Uri::parse)
            ?: return Result.failure(errorData("No folder was provided"))

        return try {
            index(treeUri)
        } catch (e: CancellationException) {
            // Cancellation is a normal outcome the user asked for. Rethrowing lets
            // WorkManager record CANCELLED rather than a spurious failure.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Indexing failed", e)
            Result.failure(errorData(e.message ?: "Indexing failed"))
        }
    }

    private suspend fun index(treeUri: Uri): Result {
        // Long enough to outlive the UI, so it must be a foreground job or the
        // system will kill it partway through and leave a half-built index.
        runCatching { setForeground(getForegroundInfo()) }
            .onFailure { Log.w(TAG, "Could not enter foreground; continuing in background", it) }

        val documents = source.findPdfs(treeUri)
        if (documents.isEmpty()) {
            return Result.success(resultData(processed = 0, failed = 0))
        }

        var processed = 0
        var failed = 0

        documents.forEachIndexed { position, document ->
            coroutineContext.ensureActive()
            publish(position, documents.size, document.displayName)

            val outcome = runCatching { repository.importAndIndex(document) }
            if (outcome.isSuccess) {
                processed++
            } else {
                failed++
                // One unreadable file must not abort the other 399. The document
                // is still recorded, with its failure noted, so it can be shown
                // in a review queue rather than silently vanishing.
                Log.w(TAG, "Could not index ${document.displayName}", outcome.exceptionOrNull())
            }
        }

        publish(documents.size, documents.size, null)
        return Result.success(resultData(processed, failed))
    }

    private suspend fun publish(completed: Int, total: Int, current: String?) {
        setProgress(
            Data.Builder()
                .putInt(KEY_COMPLETED, completed)
                .putInt(KEY_TOTAL, total)
                .putString(KEY_CURRENT, current)
                .build()
        )
        runCatching {
            setForeground(
                notifications.foregroundInfo(
                    title = "Indexing documents",
                    text = current ?: "Finishing up",
                    completed = completed,
                    total = total,
                )
            )
        }
    }

    private fun resultData(processed: Int, failed: Int): Data =
        Data.Builder().putInt(KEY_PROCESSED, processed).putInt(KEY_FAILED, failed).build()

    private fun errorData(message: String): Data =
        Data.Builder().putString(KEY_ERROR, message).build()

    companion object {
        private const val TAG = "IndexWorker"

        const val KEY_TREE_URI = "tree_uri"
        const val KEY_COMPLETED = "completed"
        const val KEY_TOTAL = "total"
        const val KEY_CURRENT = "current"
        const val KEY_PROCESSED = "processed"
        const val KEY_FAILED = "failed"
        const val KEY_ERROR = "error"
    }
}
