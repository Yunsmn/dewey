package app.dewey.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
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
 * A letterspaced label, a rule that fills the remaining width, and a count.
 *
 * This is the motif the whole interface is built on. It separates sections
 * without boxing them, which is what keeps a list of hundreds of documents from
 * turning into a wall of cards.
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
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
    ) {
        Text(
            text = label.uppercase(),
            style = Dewey.type.Label,
            color = Dewey.colors.inkMuted,
            modifier = Modifier.semantics { heading() },
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f).height(1.dp),
            color = Dewey.colors.rule,
        )
        if (count != null) {
            Text(
                text = count.toString(),
                style = Dewey.type.Label,
                color = Dewey.colors.inkFaint,
            )
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
