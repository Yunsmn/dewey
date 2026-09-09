package app.dewey.work

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.dewey.classify.CategoryLearner
import app.dewey.classify.DocumentClassifier
import app.dewey.data.db.DocumentDao
import app.dewey.data.db.FloatArrayCodec
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

    /** Where a document is going, however that was decided. */
    private data class Destination(
        val folderName: String,
        val docType: DocType,
        val margin: Float?,
    )

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
        val topLevelFolders = source.topLevelFolders(treeUri)
        val folderNames = topLevelFolderNames(topLevelFolders)

        // The categories the app ships with are thirteen guesses about somebody
        // else's life. A folder the user made is not a guess, and the documents
        // in it are labelled examples nobody had to label — so they are read
        // back as a category of their own. Measured leave-one-out on the test
        // corpus, filing by nearest folder is 100% accurate with a margin of
        // 0.089, against 0.027 for the written descriptions: a person's own
        // filing describes their documents better than any sentence we could
        // write for them.
        val vectorsByUri = documentDao.openingVectors()
            .associate { it.uri to FloatArrayCodec.decode(it.embedding) }
        val learned = CategoryLearner.learn(
            examplesByFolder(documents, topLevelFolders, vectorsByUri)
        )
        if (learned.isNotEmpty()) {
            Log.i(TAG, "Learned ${learned.size} categories from existing folders: " +
                learned.joinToString { "${it.folderName}(${it.examples.size})" })
        }

        // Deliberately not started here.
        //
        // Beginning a batch eagerly discards the previous one, so a sort that
        // turns out to move nothing — which is exactly what a second run over an
        // already-sorted folder does — would silently destroy the ability to undo
        // the first. Someone who runs the sort twice out of curiosity would lose
        // the safety net for the ninety-six files it had already moved. The batch
        // is opened on the first actual move instead.
        var batchStarted = false

        val folders = HashMap<String, Uri>()
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

            document.filedUnder(folderNames)?.let { folderName ->
                // Already in a folder, so there is nothing to move. There is
                // still something to record: the folder says what the document
                // is, and without writing that down the library shows it as
                // "Unsorted" for ever. That is what a reinstall over an
                // already-sorted folder used to look like — every file back
                // under Unsorted, with the sort insisting it had nothing to do.
                //
                // A folder of ours resolves to a type as well; one of the
                // user's has only its name, which is answer enough — the
                // library groups by folder.
                if (row != null) {
                    val type = typeForFolderName(folderName) ?: DocType.UNKNOWN
                    documentDao.recordFiledInPlace(row.id, type.name, folderName)
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

            // The learned categories go in with the built-in ones and the best
            // of the whole set wins — see DocumentClassifier.scoreAll for why
            // they are ranked together rather than asked in turn.
            //
            // The stored vector is used when there is one, so the arriving
            // document is represented exactly the way the examples it is being
            // compared against are. Falling back to the text costs an embedding
            // and is only reached for a document indexed before chunks existed.
            val stored = vectorsByUri[document.uri.toString()]
            val verdict = if (stored != null) {
                classifier.decide(stored, learned)
            } else {
                classifier.classify(row.opening, learned)
            }

            val destination = when (verdict) {
                is DocumentClassifier.Verdict.Unsure -> {
                    markForReview(row.id, verdict.reason.name, verdict.margin)
                    review++
                    return@forEachIndexed
                }

                is DocumentClassifier.Verdict.Confident -> {
                    // A learned category names its own folder. "Voiture" has no
                    // DocType and never will; the folder is the whole answer,
                    // and the library groups by it.
                    val folderName = verdict.folderName ?: verdict.type.folderName()
                    Destination(
                        folderName = folderName,
                        docType = verdict.folderName
                            ?.let { typeForFolderName(it) ?: DocType.UNKNOWN }
                            ?: verdict.type,
                        margin = verdict.margin,
                    )
                }
            }

            documentDao.recordClassification(
                row.id, destination.docType.name, null, destination.margin,
            )

            val target = folders.getOrPut(destination.folderName) {
                mover.folder(treeUri, destination.folderName) ?: Uri.EMPTY
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
                folderName = destination.folderName,
            )) {
                is DocumentMover.Outcome.Moved -> {
                    documentDao.recordMove(row.id, outcome.to.toString(), destination.folderName)
                    if (!batchStarted) {
                        undoLog.begin(UUID.randomUUID().toString(), treeUri.toString())
                        batchStarted = true
                    }
                    undoLog.record(document.undoRecord(outcome, target, destination.folderName))
                    usedFolders += destination.folderName
                    moved++
                }
                is DocumentMover.Outcome.Skipped -> {
                    Log.i(TAG, "Skipped ${document.displayName}: ${outcome.why}")
                    markForReview(row.id, outcome.why, destination.margin)
                    review++
                }
                is DocumentMover.Outcome.Failed -> {
                    Log.w(TAG, "Failed ${document.displayName}: ${outcome.why}")
                    failed++
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
            DocType.PAPER -> "Papers"
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
 * Every top-level folder, by document id, mapped to its name.
 *
 * All of them, not only the app's own thirteen. A document sitting in a folder
 * is filed — that is what a folder is — and it makes no difference whether the
 * name is one this app would have chosen. "Bills" and "Voiture" are the same
 * kind of statement: somebody decided this document goes here.
 *
 * The consequence is that the sort only ever moves documents that are loose at
 * the top level. Moving a file out of a folder the user made, into one the app
 * preferred, would be the app overruling a decision it was never asked about —
 * and those same folders are what it learns its categories from, so overruling
 * them would also be arguing with its own evidence.
 *
 * Kept apart from [SortWorker] so the rule that makes a second sort a no-op is
 * a plain function over data, testable without a CoroutineWorker.
 */
internal fun topLevelFolderNames(topLevelFolders: List<SafDocument>): Map<String, String> =
    topLevelFolders.associate { it.documentId to it.displayName }

/**
 * The documents already sitting in each top-level folder, as embeddings.
 *
 * This is the training data for [CategoryLearner], and it costs nothing to
 * collect: the folders come from the same listing that decides what is already
 * filed, and the embeddings were computed at import time.
 *
 * Folders are keyed by display name rather than document id because the name is
 * what a category is called — two folders cannot share one inside the same
 * parent, so the name identifies it. A document whose embedding is missing (never
 * indexed, or indexed before chunks were written) is skipped rather than
 * excluding its whole folder.
 *
 * A plain function over data, so the grouping can be tested without SAF.
 */
internal fun examplesByFolder(
    documents: List<SafDocument>,
    topLevelFolders: List<SafDocument>,
    vectorsByUri: Map<String, FloatArray>,
): Map<String, List<FloatArray>> {
    val folderNameById = topLevelFolders.associate { it.documentId to it.displayName }

    return documents
        .mapNotNull { document ->
            val folder = folderNameById[document.parentDocumentId] ?: return@mapNotNull null
            val vector = vectorsByUri[document.uri.toString()] ?: return@mapNotNull null
            folder to vector
        }
        .groupBy({ it.first }, { it.second })
}

/**
 * The name of the folder this document sits directly inside, or null if it is
 * loose at the top level.
 *
 * Compared by document id rather than by [SafDocument.parentUri]: the id is a
 * plain string the provider assigned, while two URIs naming the same folder
 * are not guaranteed to compare equal byte-for-byte across providers.
 */
internal fun SafDocument.filedUnder(folders: Map<String, String>): String? =
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
