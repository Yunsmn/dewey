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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dewey.ui.components.IconTile
import app.dewey.ui.components.SectionHeading
import app.dewey.ui.theme.Dewey
import app.dewey.ui.tools.ToolDestination
import app.dewey.ui.tools.hue

private const val TOOLS_PER_ROW = 4

/**
 * Every tool but the scanner, four to a row.
 *
 * Built from plain rows over [Iterable.chunked] rather than a lazy grid: the
 * whole screen is already one scrolling list (see HomeScreen), and a
 * LazyVerticalGrid nested inside a LazyColumn is exactly the crash this
 * codebase has been warned off before.
 */
@Composable
internal fun HomeToolsGrid(onOpenTool: (ToolDestination) -> Unit, modifier: Modifier = Modifier) {
    val tools = remember { ToolDestination.entries.filterNot { it == ToolDestination.SCAN } }

    Column(modifier = modifier) {
        SectionHeading(label = "Tools")
        Spacer(Modifier.height(Dewey.spacing.tight))
        tools.chunked(TOOLS_PER_ROW).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { tool ->
                    ToolCell(tool = tool, onClick = { onOpenTool(tool) }, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(Dewey.spacing.row))
        }
    }
}

@Composable
private fun ToolCell(tool: ToolDestination, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Dewey.radii.small))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Dewey.spacing.tight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // No description on the tile: the label beneath says the same thing,
        // and the clickable merges both, so a screen reader would say it twice.
        IconTile(icon = tool.icon, hue = tool.group.hue, size = 52.dp)
        Spacer(Modifier.height(Dewey.spacing.tight))
        Text(
            text = tool.shortTitle,
            style = Dewey.type.Micro,
            color = Dewey.colors.inkMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
