package app.dewey.io

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

/**
 * Best-effort removal of a document this app created but could not finish.
 *
 * SAF's create-document flow makes an empty file before handing back its Uri,
 * so any save that fails afterwards leaves that empty or partial file sitting
 * in the user's folder under the name they chose. Failing to remove it is
 * logged rather than thrown: the save has already failed, and that is the
 * error worth showing.
 */
fun ContentResolver.deleteDocumentQuietly(uri: Uri) {
    try {
        DocumentsContract.deleteDocument(this, uri)
    } catch (e: Exception) {
        Log.w(TAG, "Could not remove the incomplete document at $uri", e)
    }
}

private const val TAG = "DocumentCleanup"
