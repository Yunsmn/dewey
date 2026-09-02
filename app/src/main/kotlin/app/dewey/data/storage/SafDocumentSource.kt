package app.dewey.data.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Enumerates PDFs under a SAF document tree.
 *
 * Deliberately does not use `DocumentFile.listFiles()`. That convenience wrapper
 * issues one ContentResolver query per child and then another per attribute
 * read, so a folder of four hundred files costs well over a thousand IPC round
 * trips to a provider in another process. Querying the children URI directly
 * returns every column in a single cursor.
 */
class SafDocumentSource(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    constructor(context: Context) : this(context.contentResolver)

    /**
     * Every PDF beneath [treeUri], depth-first.
     *
     * Cooperatively cancellable: a sort the user backed out of must actually
     * stop walking, not finish the scan and discard it.
     */
    suspend fun findPdfs(treeUri: Uri): List<SafDocument> = withContext(io) {
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
            .getOrElse {
                Log.w(TAG, "Not a document tree: $treeUri", it)
                return@withContext emptyList()
            }

        val found = mutableListOf<SafDocument>()
        val pending = ArrayDeque(listOf(rootId))
        val visited = mutableSetOf<String>()

        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()
            val documentId = pending.removeFirst()
            if (!visited.add(documentId)) continue

            for (child in listChildren(treeUri, documentId)) {
                when {
                    child.isDirectory -> pending.addLast(child.documentId)
                    child.isPdf -> found += child
                }
            }
        }
        found
    }

    private fun listChildren(treeUri: Uri, parentDocumentId: String): List<SafDocument> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val children = mutableListOf<SafDocument>()

        // A provider that throws mid-enumeration should cost us that folder, not
        // the whole scan — a single unreadable subdirectory is not a reason to
        // abandon three hundred readable files.
        try {
            resolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idColumn) ?: continue
                    children += SafDocument(
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                        documentId = documentId,
                        displayName = cursor.getString(nameColumn) ?: documentId,
                        mimeType = cursor.getString(mimeColumn).orEmpty(),
                        sizeBytes = if (cursor.isNull(sizeColumn)) 0L else cursor.getLong(sizeColumn),
                        lastModified = if (cursor.isNull(modifiedColumn)) 0L else cursor.getLong(modifiedColumn),
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not list children of $parentDocumentId", e)
        }
        return children
    }

    private companion object {
        const val TAG = "SafDocumentSource"

        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}

data class SafDocument(
    val uri: Uri,
    val documentId: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long,
) {
    val isDirectory: Boolean
        get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR

    /**
     * Providers are inconsistent about MIME types — Downloads in particular hands
     * back `application/octet-stream` for plenty of real PDFs — so the extension
     * is a necessary fallback rather than belt-and-braces.
     */
    val isPdf: Boolean
        get() = mimeType == PDF_MIME || displayName.endsWith(".pdf", ignoreCase = true)

    private companion object {
        const val PDF_MIME = "application/pdf"
    }
}
