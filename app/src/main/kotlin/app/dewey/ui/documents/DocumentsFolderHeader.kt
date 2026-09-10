package app.dewey.ui.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dewey.ui.components.IconTile
import app.dewey.ui.components.PrimaryAction
import app.dewey.ui.components.SecondaryAction
import app.dewey.ui.theme.Dewey

/**
 * The part of a SAF tree document id that is actually the folder's name.
 *
 * [android.provider.DocumentsContract.getTreeDocumentId] hands back an
 * authority-specific id such as `primary:Download` or, on some providers,
 * a path like `raw:/storage/emulated/0/Download` — the folder's own name is
 * whatever sits after the last `:` or `/`, whichever comes later. Kept a plain
 * function, no [android.net.Uri] in sight, so the id itself is what a test
 * supplies rather than a framework class only Robolectric can construct.
 */
internal fun folderDisplayName(documentId: String): String =
    documentId.substringAfterLast(':').substringAfterLast('/')

/**
 * "Documents", the linked folder if there is one, and the way to link or
 * change it.
 *
 * A folder is where this whole tab starts — everything below only exists
 * because one was granted — so the header says which folder that is rather
 * than leaving it to be inferred from what shows up in the list.
 */
@Composable
fun DocumentsFolderHeader(
    folderName: String?,
    documentCount: Int,
    onAddFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text("Documents", style = Dewey.type.Display, color = Dewey.colors.ink)
        Spacer(Modifier.height(Dewey.spacing.gutter))

        if (folderName == null) {
            Text(
                text = "Link a folder and Dewey will keep everything in it here — " +
                    "sorted, searchable, and ready to ask about.",
                style = Dewey.type.Body,
                color = Dewey.colors.inkMuted,
            )
            Spacer(Modifier.height(Dewey.spacing.row))
            PrimaryAction(label = "Link a folder", onClick = onAddFolder, icon = Icons.Rounded.Folder)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
            ) {
                IconTile(icon = Icons.Rounded.Folder, hue = Dewey.colors.hues.pages, size = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(folderName, style = Dewey.type.Title, color = Dewey.colors.ink)
                    Text(countLabel(documentCount, "document"), style = Dewey.type.Meta, color = Dewey.colors.inkMuted)
                }
                SecondaryAction(label = "Change", onClick = onAddFolder)
            }
        }
    }
}

/** "1 document", "7 documents" — the count a person would actually write. */
private fun countLabel(count: Int, noun: String): String = if (count == 1) "1 $noun" else "$count ${noun}s"
