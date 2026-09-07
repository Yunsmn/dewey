package app.dewey.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme

/**
 * One document in the library.
 *
 * The title is serif because it is what you read; the filename and amount are
 * monospaced because they are machine strings you scan. Showing the real
 * filename matters — `Scan_20240312_004.pdf` beside "Lydec, janvier 2023" is the
 * product's entire argument in one line.
 *
 * Before a document has been classified there is no such title, only the
 * useless filename. Repeating it in both lines would waste the row's strongest
 * position on a string that already means nothing, so an untitled document
 * leads with its filename in mono and says plainly that it is unread.
 */
@Composable
fun DocumentRow(
    title: String?,
    subtitle: String?,
    filename: String,
    trailing: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Overridable for the one case that needs it: an overdue bill's due date
    // reads in Dewey.colors.attention rather than the usual muted ink. See
    // app.dewey.ui.bills.BillsScreen.
    subtitleColor: Color = Dewey.colors.inkMuted,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Pressing tints the paper rather than raising a ripple: the surface
            // is meant to read as a page, and a page does not float.
            .background(if (pressed) Dewey.colors.paperSunken else Dewey.colors.paper)
            .clickable(interactionSource = interactions, indication = null, onClick = onClick)
            .padding(vertical = Dewey.spacing.row),
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (title != null) {
                Text(
                    text = title,
                    style = Dewey.type.Title,
                    color = Dewey.colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = filename,
                    style = Dewey.type.Mono.copy(fontSize = 15.sp),
                    color = Dewey.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = Dewey.type.Meta,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            // Only worth repeating when the title is something else.
            if (title != null) {
                Text(
                    text = filename,
                    style = Dewey.type.Mono,
                    color = Dewey.colors.inkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.padding(top = Dewey.spacing.tight),
                )
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = Dewey.type.Mono,
                color = Dewey.colors.ink,
                modifier = Modifier.width(96.dp).padding(top = 3.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F2, widthDp = 380)
@Composable
private fun DocumentRowPreview() {
    DeweyTheme {
        Column(modifier = Modifier.padding(20.dp)) {
            DocumentRow(
                title = "Lydec · janvier 2023",
                subtitle = "Electricity and water · Casablanca",
                filename = "document (5).pdf",
                trailing = "281.26",
                onClick = {},
            )
            DocumentRow(
                title = "Attijariwafa Bank",
                subtitle = "Account statement · fevrier 2024",
                filename = "WhatsApp Doc 2022-01-20 at 10.22.24.pdf",
                onClick = {},
            )
            // Not yet classified: leads with the filename, says so.
            DocumentRow(
                title = null,
                subtitle = "48 pages · not yet read",
                filename = "IMG_8262.pdf",
                onClick = {},
            )
        }
    }
}
