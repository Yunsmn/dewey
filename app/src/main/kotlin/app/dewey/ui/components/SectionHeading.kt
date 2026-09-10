package app.dewey.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme

/**
 * A sentence-case label and, when there's a count worth showing, a small pill
 * beside it.
 *
 * Small caps and a filling rule read as a catalogue index; a soft-cornered
 * screen with icon tiles everywhere wants its section breaks to sound like
 * the rest of it. The count moved off the end of a divider and into its own
 * pill for the same reason a number sitting alone on a rule used to look like
 * a page number that wandered off — here it reads as a count of something.
 */
@Composable
fun SectionHeading(
    label: String,
    count: Int? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = Dewey.spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight),
    ) {
        Text(
            text = label,
            style = Dewey.type.Title,
            color = Dewey.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (count != null) {
            Box(
                modifier = Modifier
                    .background(Dewey.colors.paperSunken, RoundedCornerShape(999.dp))
                    .padding(horizontal = Dewey.spacing.tight, vertical = 2.dp),
            ) {
                Text(
                    text = count.toString(),
                    style = Dewey.type.Meta,
                    color = Dewey.colors.inkMuted,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F2)
@Composable
private fun SectionHeadingPreview() {
    DeweyTheme {
        SectionHeading(label = "Bills", count = 14, modifier = Modifier.padding(20.dp))
    }
}
