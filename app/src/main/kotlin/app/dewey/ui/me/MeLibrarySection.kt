package app.dewey.ui.me

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dewey.ui.components.GlassCard
import app.dewey.ui.components.IconTile
import app.dewey.ui.components.SecondaryAction
import app.dewey.ui.theme.Dewey

/**
 * The folder Documents reads from, and the way to change it.
 *
 * [onChangeFolder] is wired by the caller to the same picker-and-grant path
 * [app.dewey.ui.library.LibraryViewModel.onFolderGranted] runs for the
 * Documents tab, so picking a folder here starts the same indexing job
 * rather than a second one this screen would have to invent.
 */
@Composable
fun MeLibrarySection(folderName: String?, documentCount: Int, onChangeFolder: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Folder, Dewey.colors.hues.pages, size = 40.dp)
            Spacer(Modifier.width(Dewey.spacing.row))
            Column(modifier = Modifier.weight(1f)) {
                Text(folderName ?: "No folder linked", style = Dewey.type.Title, color = Dewey.colors.ink)
                Text(documentCountLabel(documentCount), style = Dewey.type.Meta, color = Dewey.colors.inkMuted)
            }
            Spacer(Modifier.width(Dewey.spacing.row))
            SecondaryAction(label = if (folderName == null) "Link" else "Change", onClick = onChangeFolder)
        }
    }
}

/** "1 document", "7 documents" — the same wording as [app.dewey.ui.documents.DocumentsFolderHeader]'s own count. */
private fun documentCountLabel(count: Int): String = if (count == 1) "1 document" else "$count documents"
