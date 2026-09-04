package app.dewey.sort

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Creates folders and moves documents into them, over SAF.
 *
 * Every operation here is destructive to someone's filing, so the rules are:
 * never overwrite, never assume a provider supports an operation, and report
 * what actually happened rather than what was attempted. The caller records each
 * completed move so it can be undone — see [UndoLog].
 */
class DocumentMover(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    sealed interface Outcome {
        data class Moved(val from: Uri, val to: Uri, val folder: String) : Outcome
        data class Skipped(val uri: Uri, val why: String) : Outcome
        data class Failed(val uri: Uri, val why: String) : Outcome
    }

    /**
     * The document URI for a directory, given either form.
     *
     * A tree URI and a document URI look alike and are not interchangeable:
     * `createDocument` and `moveDocument` take document URIs and reject a tree
     * URI outright with "Invalid URI". The picker hands back a tree, so every
     * write path has to convert first.
     */
    private fun asDocumentUri(uri: Uri): Uri =
        if (isTreeOnly(uri)) {
            DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        } else {
            uri
        }

    /**
     * Finds or creates a subfolder of [parent].
     *
     * Returns the existing folder when one is already there. Creating a second
     * "Bills" beside the user's own would be its own kind of mess.
     */
    suspend fun folder(parent: Uri, name: String): Uri? = withContext(io) {
        existingChild(parent, name)?.let { return@withContext it }

        try {
            DocumentsContract.createDocument(
                resolver,
                asDocumentUri(parent),
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not create folder '$name'", e)
            null
        }
    }

    /**
     * Moves [document] out of [sourceParent] and into [targetParent].
     *
     * Prefers the provider's own move, which is atomic and keeps the document's
     * identity. Falls back to copy-then-delete for providers that do not support
     * moving — and deletes only after the copy is confirmed, so a failure leaves
     * the original where it was rather than losing it.
     */
    suspend fun move(
        document: Uri,
        displayName: String,
        sourceParent: Uri,
        targetParent: Uri,
        folderName: String,
    ): Outcome = withContext(io) {
        if (sourceParent == targetParent) {
            return@withContext Outcome.Skipped(document, "already in $folderName")
        }
        if (existingChild(targetParent, displayName) != null) {
            // Refusing is right: the alternative is either overwriting a file the
            // user has, or silently renaming theirs or ours.
            return@withContext Outcome.Skipped(document, "a file of that name is already in $folderName")
        }

        moveNatively(document, sourceParent, targetParent)?.let {
            return@withContext Outcome.Moved(document, it, folderName)
        }

        copyThenDelete(document, targetParent, folderName)
    }

    private fun moveNatively(document: Uri, sourceParent: Uri, targetParent: Uri): Uri? =
        try {
            DocumentsContract.moveDocument(
                resolver,
                document,
                asDocumentUri(sourceParent),
                asDocumentUri(targetParent),
            )
        } catch (e: UnsupportedOperationException) {
            Log.i(TAG, "Provider cannot move; falling back to copy")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Move failed for $document", e)
            null
        }

    private fun copyThenDelete(document: Uri, targetParent: Uri, folderName: String): Outcome {
        val copy = try {
            DocumentsContract.copyDocument(resolver, document, asDocumentUri(targetParent))
        } catch (e: Exception) {
            Log.w(TAG, "Copy failed for $document", e)
            null
        } ?: return Outcome.Failed(document, "could not be copied into $folderName")

        val removed = try {
            DocumentsContract.deleteDocument(resolver, document)
        } catch (e: Exception) {
            Log.w(TAG, "Delete after copy failed for $document", e)
            false
        }

        // A copy that could not be followed by a delete leaves two files. Saying
        // so is better than reporting a clean move and letting the user find the
        // duplicate later.
        return if (removed) {
            Outcome.Moved(document, copy, folderName)
        } else {
            Outcome.Skipped(copy, "copied into $folderName but the original could not be removed")
        }
    }

    /** The child of [parent] with this display name, if it exists. */
    private fun existingChild(parent: Uri, name: String): Uri? {
        // Tree URIs carry a tree document id, document URIs a document id, and
        // asking for the wrong one throws rather than returning null.
        val parentId = runCatching {
            if (isTreeOnly(parent)) {
                DocumentsContract.getTreeDocumentId(parent)
            } else {
                DocumentsContract.getDocumentId(parent)
            }
        }.getOrNull() ?: return null

        val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentId)

        return try {
            resolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == name) {
                        return@use DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(0))
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not list $parent", e)
            null
        }
    }

    companion object {
        private const val TAG = "DocumentMover"

        private const val SEGMENT_TREE = "tree"
        private const val SEGMENT_DOCUMENT = "document"

        /**
         * True for a bare tree URI — one naming a tree but no document within it.
         *
         * Decided from the URI's own shape rather than
         * `DocumentsContract.isDocumentUri`, which needs a Context and throws a
         * NullPointerException without one. The three forms are:
         *
         *     content://auth/tree/<treeId>                       bare tree
         *     content://auth/tree/<treeId>/document/<docId>      document in a tree
         *     content://auth/document/<docId>                    plain document
         *
         * Only the first needs converting before a write.
         */
        fun isTreeOnly(uri: Uri): Boolean = isTreeOnly(uri.pathSegments)

        /**
         * The same decision over path segments alone.
         *
         * Split out so it can be tested as ordinary Kotlin: the Uri overload
         * would drag in Robolectric, which for API 36 requires a JDK this build
         * does not use. The rule has nothing to do with Android anyway.
         */
        fun isTreeOnly(segments: List<String>): Boolean =
            segments.firstOrNull() == SEGMENT_TREE && !segments.contains(SEGMENT_DOCUMENT)
    }
}
