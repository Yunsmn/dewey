package app.dewey.ui.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.dewey.ui.components.GlassCard
import app.dewey.ui.components.IconTile
import app.dewey.ui.components.NavBarClearance
import app.dewey.ui.components.PrimaryAction
import app.dewey.ui.components.SecondaryAction
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.theme.Hue

/**
 * The layout every PDF tool shares: what it does, its inputs, one action, and
 * the outcome.
 *
 * Shared so eleven tools do not look like eleven apps. Each screen supplies its
 * inputs as [content]; this owns the title, the run button, and how Running,
 * Done and Failed are shown, which is where screens drift apart fastest when
 * each draws its own.
 *
 * @param canRun whether the inputs are complete. The button stays visible but
 *   dimmed and inert until they are, rather than disappearing — a button that
 *   appears only once the form is valid gives no hint of what it is waiting for.
 * @param hue and @param icon together put a large [IconTile] beside the title,
 *   the same colour the tool wears on the Home grid — see [ToolGroup.hue]. Both
 *   are optional and default to null so a screen that hasn't adopted this yet
 *   still compiles and still shows a title, just without the tile.
 */
@Composable
fun ToolScaffold(
    title: String,
    description: String,
    state: ToolRunState,
    runLabel: String,
    canRun: Boolean,
    onRun: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    hue: Hue? = null,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val running = state is ToolRunState.Running
    val enabled = canRun && !running

    Column(
        modifier = modifier
            .fillMaxSize()
            // Below the status bar: the app draws edge to edge, and without
            // this the title sits under the clock. Before the scroll, so the
            // content scrolls beneath the inset rather than into it.
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = Dewey.spacing.gutter, end = Dewey.spacing.gutter, top = Dewey.spacing.block, bottom = NavBarClearance),
        verticalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
    ) {
        if (hue != null && icon != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row)) {
                IconTile(icon = icon, hue = hue, size = 56.dp)
                Text(title, style = Dewey.type.Headline, color = Dewey.colors.ink)
            }
        } else {
            Text(title, style = Dewey.type.Display, color = Dewey.colors.ink)
        }
        Text(description, style = Dewey.type.Body, color = Dewey.colors.inkMuted)
        Spacer(Modifier.height(Dewey.spacing.tight))

        content()

        Spacer(Modifier.height(Dewey.spacing.tight))
        PrimaryAction(
            label = if (running) "Working…" else runLabel,
            onClick = { if (enabled) onRun() },
            modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f),
        )

        AnimatedVisibility(visible = state !is ToolRunState.Idle) {
            Outcome(state = state, onReset = onReset)
        }
    }
}

@Composable
private fun Outcome(state: ToolRunState, onReset: () -> Unit) {
    when (state) {
        ToolRunState.Idle -> Unit
        ToolRunState.Running -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
            modifier = Modifier.padding(vertical = Dewey.spacing.tight),
        ) {
            CircularProgressIndicator(
                color = Dewey.colors.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            Text("This can take a moment on a long document.", style = Dewey.type.Meta, color = Dewey.colors.inkMuted)
        }
        is ToolRunState.Done -> GlassCard(modifier = Modifier.fillMaxWidth(), accent = Dewey.colors.accent) {
            Text("Done", style = Dewey.type.Title, color = Dewey.colors.ink)
            Spacer(Modifier.height(Dewey.spacing.hairline))
            Text(state.summary, style = Dewey.type.Body, color = Dewey.colors.inkMuted)
            Spacer(Modifier.height(Dewey.spacing.row))
            SecondaryAction(label = "Start over", onClick = onReset)
        }
        is ToolRunState.Failed -> GlassCard(modifier = Modifier.fillMaxWidth(), accent = Dewey.colors.danger) {
            Text("Didn't work", style = Dewey.type.Title, color = Dewey.colors.ink)
            Spacer(Modifier.height(Dewey.spacing.hairline))
            Text(state.message, style = Dewey.type.Body, color = Dewey.colors.inkMuted)
            Spacer(Modifier.height(Dewey.spacing.row))
            SecondaryAction(label = "Try again", onClick = onReset)
        }
    }
}

/**
 * One input slot for a file: its name and size once chosen, a prompt before.
 * The whole slot is the tap target, so there is no small "Browse" button to
 * aim for.
 *
 * Sunken rather than raised — [Dewey.colors.paperSunken], no shadow, no
 * border — so it reads as a place a file goes rather than as one more card
 * competing with the ones around it. An icon carries what used to be carried
 * by an accent-coloured word of text: [Icons.Rounded.UploadFile] before a
 * file is chosen, [Icons.Rounded.InsertDriveFile] once one is.
 */
@Composable
fun FileSlot(file: PickedFile?, prompt: String, onPick: () -> Unit, modifier: Modifier = Modifier, hue: Hue? = null) {
    val tint = hue?.strong ?: Dewey.colors.accent
    val shape = RoundedCornerShape(Dewey.radii.medium)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Dewey.colors.paperSunken)
            .clickable(onClick = onPick)
            .padding(Dewey.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
    ) {
        IconTile(
            icon = if (file == null) Icons.Rounded.UploadFile else Icons.Rounded.InsertDriveFile,
            hue = hue ?: Hue(tint, tint.copy(alpha = 0.14f)),
            size = 40.dp,
        )
        if (file == null) {
            Text(prompt, style = Dewey.type.Body, color = Dewey.colors.ink)
        } else {
            Column {
                Text(
                    text = file.name.ifEmpty { "Chosen file" },
                    style = Dewey.type.Mono,
                    color = Dewey.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Dewey.spacing.hairline))
                Text("${formatBytes(file.sizeBytes)} · tap to change", style = Dewey.type.Meta, color = Dewey.colors.inkMuted)
            }
        }
    }
}

/** A field label above an input, in sentence case rather than shouted small caps. */
@Composable
fun FieldLabel(text: String) {
    Text(text, style = Dewey.type.Label, color = Dewey.colors.inkMuted)
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 700, widthDp = 390)
@Composable
private fun ToolScaffoldPreview() {
    DeweyTheme {
        ToolScaffold(
            title = "Rotate",
            description = "Turn pages a quarter at a time. The original stays as it is.",
            state = ToolRunState.Failed("There is no page 9 — this document has 4 pages."),
            runLabel = "Rotate and save",
            canRun = true,
            onRun = {},
            onReset = {},
        ) {
            FieldLabel("Document")
            FileSlot(file = PickedFile(android.net.Uri.EMPTY, "lease.pdf", 482_000), prompt = "Choose a PDF", onPick = {})
        }
    }
}
