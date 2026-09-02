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
                parent,
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
            DocumentsContract.moveDocument(resolver, document, sourceParent, targetParent)
        } catch (e: UnsupportedOperationException) {
            Log.i(TAG, "Provider cannot move; falling back to copy")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Move failed for $document", e)
            null
        }

    private fun copyThenDelete(document: Uri, targetParent: Uri, folderName: String): Outcome {
        val copy = try {
            DocumentsContract.copyDocument(resolver, document, targetParent)
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
        val parentId = runCatching { DocumentsContract.getDocumentId(parent) }
            .recoverCatching { DocumentsContract.getTreeDocumentId(parent) }
            .getOrNull() ?: return null

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

    private companion object {
        const val TAG = "DocumentMover"
    }
}
