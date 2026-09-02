package app.dewey.work

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.dewey.classify.DocumentClassifier
import app.dewey.data.db.DocumentDao
import app.dewey.data.storage.SafDocumentSource
import app.dewey.domain.model.DocType
import app.dewey.sort.DocumentMover
import app.dewey.sort.UndoLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.util.UUID
import kotlin.coroutines.coroutineContext

/**
 * Reads every document in a folder, works out what each one is, and files it.
 *
 * The shape that matters is what it does with doubt. Anything the classifier is
 * confident about is moved without asking; anything it is not is left exactly
 * where it is and surfaced for review. Four hundred confirmation dialogs is not
 * a feature, and neither is confidently filing a research paper under Insurance.
 *
 * Every completed move is written to [UndoLog] as it happens, so an interrupted
 * sort is still undoable.
 */
class SortWorker(
    context: Context,
    params: WorkerParameters,
    private val source: SafDocumentSource,
    private val documentDao: DocumentDao,
    private val classifier: DocumentClassifier,
    private val mover: DocumentMover,
    private val undoLog: UndoLog,
    private val notifications: TaskNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        notifications.foregroundInfo("Sorting documents", "Starting…", 0, 0)

    override suspend fun doWork(): Result {
        val treeUri = inputData.getString(KEY_TREE_URI)?.let(Uri::parse)
            ?: return Result.failure(Data.Builder().putString(KEY_ERROR, "No folder was provided").build())

        return try {
            sort(treeUri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Sort failed", e)
            Result.failure(Data.Builder().putString(KEY_ERROR, e.message ?: "Sort failed").build())
        }
    }

    private suspend fun sort(treeUri: Uri): Result {
        runCatching { setForeground(getForegroundInfo()) }

        val documents = source.findPdfs(treeUri)
        if (documents.isEmpty()) return Result.success(summary(0, 0, 0))

        // Indexed text is keyed by URI: sorting classifies from text that was
        // already extracted at import rather than re-reading every PDF, which is
        // the difference between a minute and twenty.
        val textByUri = documentDao.allIndexed().associateBy { it.uri }

        undoLog.begin(UUID.randomUUID().toString(), treeUri.toString())

        val folders = HashMap<DocType, Uri>()
        var moved = 0
        var review = 0
        var failed = 0

        documents.forEachIndexed { position, document ->
            coroutineContext.ensureActive()
            publish(position, documents.size, document.displayName)

            val row = textByUri[document.uri.toString()]
            if (row?.text.isNullOrBlank()) {
                // Never indexed, or nothing readable in it. Not a failure — it
                // simply cannot be classified, so it stays put.
                markForReview(row?.id, DocumentClassifier.Verdict.Reason.NO_TEXT.name, null)
                review++
                return@forEachIndexed
            }

            when (val verdict = classifier.classify(row!!.text!!)) {
                is DocumentClassifier.Verdict.Unsure -> {
                    markForReview(row.id, verdict.reason.name, verdict.margin)
                    review++
                }

                is DocumentClassifier.Verdict.Confident -> {
                    documentDao.recordClassification(row.id, verdict.type.name, null, verdict.margin)

                    val folderName = verdict.type.folderName()
                    val target = folders.getOrPut(verdict.type) {
                        mover.folder(treeUri, folderName) ?: Uri.EMPTY
                    }
                    if (target == Uri.EMPTY) {
                        failed++
                        return@forEachIndexed
                    }

                    when (val outcome = mover.move(
                        document = document.uri,
                        displayName = document.displayName,
                        sourceParent = treeUri,
                        targetParent = target,
                        folderName = folderName,
                    )) {
                        is DocumentMover.Outcome.Moved -> {
                            documentDao.recordMove(row.id, outcome.to.toString(), folderName)
                            undoLog.record(
                                UndoLog.Move(
                                    documentUri = outcome.from.toString(),
                                    movedToUri = outcome.to.toString(),
                                    originalParentUri = treeUri.toString(),
                                    targetParentUri = target.toString(),
                                    displayName = document.displayName,
                                    folderName = folderName,
                                )
                            )
                            moved++
                        }
                        is DocumentMover.Outcome.Skipped -> {
                            Log.i(TAG, "Skipped ${document.displayName}: ${outcome.why}")
                            review++
                        }
                        is DocumentMover.Outcome.Failed -> {
                            Log.w(TAG, "Failed ${document.displayName}: ${outcome.why}")
                            failed++
                        }
                    }
                }
            }
        }

        publish(documents.size, documents.size, null)
        return Result.success(summary(moved, review, failed))
    }

    private suspend fun markForReview(id: Long?, reason: String, margin: Float?) {
        if (id == null) return
        documentDao.recordClassification(id, DocType.UNKNOWN.name, reason, margin)
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
            setForeground(notifications.foregroundInfo("Sorting documents", current ?: "Finishing up", completed, total))
        }
    }

    private fun summary(moved: Int, review: Int, failed: Int): Data =
        Data.Builder()
            .putInt(KEY_MOVED, moved)
            .putInt(KEY_REVIEW, review)
            .putInt(IndexWorker.KEY_FAILED, failed)
            .build()

    companion object {
        private const val TAG = "SortWorker"

        const val KEY_TREE_URI = "tree_uri"
        const val KEY_MOVED = "moved"
        const val KEY_REVIEW = "review"
        const val KEY_ERROR = "error"

        /** Folder names as a person would write them, not enum constants. */
        fun DocType.folderName(): String = when (this) {
            DocType.UTILITY_BILL -> "Bills"
            DocType.BANK_STATEMENT -> "Bank"
            DocType.INVOICE -> "Receipts"
            DocType.RENTAL_CONTRACT -> "Contracts"
            DocType.MEDICAL -> "Medical"
            DocType.UNIVERSITY -> "University"
            DocType.INSURANCE -> "Insurance"
            DocType.EMPLOYMENT -> "Employment"
            DocType.TAX -> "Tax"
            DocType.WARRANTY -> "Warranties"
            DocType.ADMIN -> "Administrative"
            DocType.TRAVEL -> "Travel"
            DocType.UNKNOWN -> "Unsorted"
        }
    }
}
