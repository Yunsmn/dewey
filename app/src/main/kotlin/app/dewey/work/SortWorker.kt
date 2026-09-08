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
import app.dewey.data.storage.SafDocument
import app.dewey.data.storage.SafDocumentSource
import app.dewey.domain.model.DocType
import app.dewey.sort.DocumentMover
import app.dewey.sort.UndoLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID

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
        if (documents.isEmpty()) return Result.success(summary(moved = 0, review = 0, folders = 0, failed = 0))

        // Indexed text is keyed by URI: sorting classifies from text that was
        // already extracted at import rather than re-reading every PDF, which is
        // the difference between a minute and twenty.
        //
        // Only the opening of each document is read. The classifier looks at no
        // more than that, and pulling every document's full text into one map
        // is how a four-hundred-file sort runs out of heap — see
        // DocumentDao.allIndexedOpenings.
        val openingByUri = documentDao
            .allIndexedOpenings(DocumentClassifier.OPENING_CHARS)
            .associateBy { it.uri }

        // findPdfs() walks subfolders, so a second sort re-enumerates everything
        // an earlier run already filed into Bills, Bank, and so on. Recognising
        // those by their parent folder — rather than by re-classifying them —
        // is what makes a rerun a no-op instead of a wall of false reviews.
        val alreadyFiledFolders = alreadyFiledFolders(source.topLevelFolders(treeUri))

        // Deliberately not started here.
        //
        // Beginning a batch eagerly discards the previous one, so a sort that
        // turns out to move nothing — which is exactly what a second run over an
        // already-sorted folder does — would silently destroy the ability to undo
        // the first. Someone who runs the sort twice out of curiosity would lose
        // the safety net for the ninety-six files it had already moved. The batch
        // is opened on the first actual move instead.
        var batchStarted = false

        val folders = HashMap<DocType, Uri>()
        val usedFolders = HashSet<String>()
        var moved = 0
        var review = 0
        var failed = 0

        // Documents an earlier run (or the user) had already filed. Counted
        // apart from `moved` because nothing moved — but not silently, because
        // a rerun that recognises ninety-six documents has done something.
        var recognised = 0

        documents.forEachIndexed { position, document ->
            // currentCoroutineContext() rather than the bare name — see IndexWorker
            // for why a CoroutineWorker's own `coroutineContext` cannot cancel.
            currentCoroutineContext().ensureActive()
            publish(position, documents.size, document.displayName)

            val row = openingByUri[document.uri.toString()]

            document.filedUnder(alreadyFiledFolders)?.let { type ->
                // Already sitting in one of our folders, so there is nothing to
                // move. There is still something to record: the folder says what
                // the document is, and without writing that down the library
                // shows it as "Unsorted" for ever. That is what a reinstall over
                // an already-sorted folder used to look like — every file back
                // under Unsorted, with the sort insisting it had nothing to do.
                if (row != null) {
                    documentDao.recordFiledInPlace(row.id, type.name, type.folderName())
                    recognised++
                }
                return@forEachIndexed
            }

            if (row?.opening.isNullOrBlank()) {
                // Never indexed, or nothing readable in it. Not a failure — it
                // simply cannot be classified, so it stays put.
                markForReview(row?.id, DocumentClassifier.Verdict.Reason.NO_TEXT.name, null)
                review++
                return@forEachIndexed
            }

            when (val verdict = classifier.classify(row.opening)) {
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
                        sourceParent = document.parentUri,
                        targetParent = target,
                        folderName = folderName,
                    )) {
                        is DocumentMover.Outcome.Moved -> {
                            documentDao.recordMove(row.id, outcome.to.toString(), folderName)
                            if (!batchStarted) {
                                undoLog.begin(UUID.randomUUID().toString(), treeUri.toString())
                                batchStarted = true
                            }
                            undoLog.record(document.undoRecord(outcome, target, folderName))
                            usedFolders += folderName
                            moved++
                        }
                        is DocumentMover.Outcome.Skipped -> {
                            Log.i(TAG, "Skipped ${document.displayName}: ${outcome.why}")
                            markForReview(row.id, outcome.why, verdict.margin)
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
        return Result.success(summary(moved, review, usedFolders.size, failed, recognised))
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

    private fun summary(moved: Int, review: Int, folders: Int, failed: Int, recognised: Int = 0): Data =
        Data.Builder()
            .putInt(KEY_MOVED, moved)
            .putInt(KEY_REVIEW, review)
            .putInt(KEY_FOLDERS, folders)
            .putInt(KEY_RECOGNISED, recognised)
            .putInt(IndexWorker.KEY_FAILED, failed)
            .build()

    companion object {
        private const val TAG = "SortWorker"

        const val KEY_TREE_URI = "tree_uri"
        const val KEY_MOVED = "moved"
        const val KEY_REVIEW = "review"
        const val KEY_FOLDERS = "folders"
        const val KEY_RECOGNISED = "recognised"
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


        /**
         * The type a folder of this name stands for, or null if it is not one
         * of ours.
         *
         * [DocType.UNKNOWN] is excluded deliberately. Its folder is "Unsorted",
         * and a document in there is precisely one nothing is known about —
         * treating that as an answer would mark it filed and stop the next sort
         * ever looking at it again.
         */
        fun typeForFolderName(name: String): DocType? =
            DocType.entries.firstOrNull { it != DocType.UNKNOWN && it.folderName() == name }
    }
}

/**
 * The app's own category folders among [topLevelFolders], by document id, each
 * mapped to the type its name stands for.
 *
 * A map rather than a set of ids, because the folder a document sits in is an
 * answer and not just a flag. A file inside "Bills" is a bill — that is what
 * putting it there meant, whether this app did it on an earlier run or the
 * person did it by hand — and skipping it without recording that throws the
 * answer away. See [SortWorker.sort].
 *
 * Kept apart from [SortWorker] so the rule that makes a second sort a no-op is
 * a plain function over data, testable without a CoroutineWorker.
 */
internal fun alreadyFiledFolders(topLevelFolders: List<SafDocument>): Map<String, DocType> =
    topLevelFolders.mapNotNull { folder ->
        SortWorker.typeForFolderName(folder.displayName)?.let { folder.documentId to it }
    }.toMap()

/**
 * The category of the folder this document sits directly inside, or null if it
 * is not in one of ours.
 *
 * Compared by document id rather than by [SafDocument.parentUri]: the id is a
 * plain string the provider assigned, while two URIs naming the same folder
 * are not guaranteed to compare equal byte-for-byte across providers.
 */
internal fun SafDocument.filedUnder(folders: Map<String, DocType>): DocType? =
    folders[parentDocumentId]

/**
 * The undo entry for a document [SortWorker] just moved.
 *
 * [UndoLog.Move.originalParentUri] is this document's own parent, not the
 * root of the tree being sorted — a document that started inside a subfolder
 * must be put back there, not dumped at the top level.
 */
internal fun SafDocument.undoRecord(
    outcome: DocumentMover.Outcome.Moved,
    targetParent: Uri,
    folderName: String,
): UndoLog.Move = UndoLog.Move(
    documentUri = outcome.from.toString(),
    movedToUri = outcome.to.toString(),
    originalParentUri = parentUri.toString(),
    targetParentUri = targetParent.toString(),
    displayName = displayName,
    folderName = folderName,
)
