package app.dewey.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dewey.data.recent.RecentFile
import app.dewey.data.recent.RecentKind
import app.dewey.ui.components.IconTile
import app.dewey.ui.components.SectionHeading
import app.dewey.ui.theme.Dewey

/**
 * The files Dewey made most recently - what a scanner app usually calls its
 * "last scans", widened to tool output too. [recent] arrives already capped
 * (see HomeViewModel.HOME_RECENT_LIMIT); this composable only lays it out.
 */
@Composable
internal fun HomeRecentSection(
    recent: List<RecentFile>,
    now: Long,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionHeading(label = "Recent")
        Spacer(Modifier.height(Dewey.spacing.tight))
        if (recent.isEmpty()) {
            EmptyRecent()
        } else {
            recent.forEach { file ->
                RecentRow(file = file, now = now, onClick = { onOpenFile(file.uri) })
            }
        }
    }
}

@Composable
private fun RecentRow(file: RecentFile, now: Long, onClick: () -> Unit) {
    val (icon, hue) = when (file.kind) {
        RecentKind.SCAN -> Icons.Rounded.DocumentScanner to Dewey.colors.hues.scan
        RecentKind.TOOL -> Icons.Rounded.PictureAsPdf to Dewey.colors.hues.convert
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dewey.radii.small))
            .clickable(onClick = onClick)
            .padding(vertical = Dewey.spacing.row),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
    ) {
        IconTile(icon = icon, hue = hue, size = 44.dp)
        Text(
            text = file.name,
            style = Dewey.type.Body,
            color = Dewey.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = relativeTime(file.createdAt, now),
            style = Dewey.type.Meta,
            color = Dewey.colors.inkFaint,
        )
    }
}

@Composable
private fun EmptyRecent() {
    Text(
        text = "Your scans and edited PDFs will show up here.",
        style = Dewey.type.Body,
        color = Dewey.colors.inkMuted,
    )
}
