package app.dewey.ui.tools

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** A file the user chose, with what a screen needs to show and size-check it. */
data class PickedFile(val uri: Uri, val name: String, val sizeBytes: Long)

/**
 * Reads a picked document's display name and size.
 *
 * The URI's last path segment is not a name — on most providers it is an
 * opaque id like "document:1043" — so the provider is asked. Size 0 means the
 * provider would not say, which the engines already treat as unknown rather
 * than empty.
 */
fun ContentResolver.describe(uri: Uri): PickedFile {
    val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    val described = runCatching {
        query(uri, columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            PickedFile(
                uri = uri,
                name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else "",
                sizeBytes = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L,
            )
        }
    }.onFailure { Log.w(TAG, "Could not describe $uri", it) }.getOrNull()

    return described ?: PickedFile(uri, name = "", sizeBytes = 0L)
}

/** Opens the system picker for one PDF. Returns the function that launches it. */
@Composable
fun rememberPdfPicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onPicked(resolver.describe(it)) }
    }
    return remember(launcher) { { launcher.launch(arrayOf(PDF_MIME)) } }
}

/** Opens the system picker for several PDFs, in the order the user chose them. */
@Composable
fun rememberPdfsPicker(onPicked: (List<PickedFile>) -> Unit): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onPicked(uris.map(resolver::describe))
    }
    return remember(launcher) { { launcher.launch(arrayOf(PDF_MIME)) } }
}

/** Opens the system picker for several images. */
@Composable
fun rememberImagesPicker(onPicked: (List<PickedFile>) -> Unit): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onPicked(uris.map(resolver::describe))
    }
    return remember(launcher) { { launcher.launch(arrayOf("image/jpeg", "image/png", "image/webp")) } }
}

/**
 * Opens the system picker for a folder, for results that are more than one
 * file. Note Android refuses the Downloads root itself; a folder inside it is
 * fine.
 */
@Composable
fun rememberFolderPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(onPicked)
    }
    return remember(launcher) { { launcher.launch(null) } }
}

/**
 * Asks the user where to save a single result.
 *
 * Every tool writes to a new file the user names, never over its input: the
 * engines are built that way, and this is how a screen gets the new file.
 * Returns a function taking the suggested name — see [derivedFileName].
 */
@Composable
fun rememberSaveAs(mimeType: String = PDF_MIME, onCreated: (Uri) -> Unit): (String) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        uri?.let(onCreated)
    }
    return remember(launcher) { { suggested: String -> launcher.launch(suggested) } }
}

const val PDF_MIME = "application/pdf"

private const val TAG = "DocumentPickers"
