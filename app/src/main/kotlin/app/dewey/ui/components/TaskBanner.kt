package app.dewey.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.work.TaskState

/**
 * What the background job is doing, and a way to stop it.
 *
 * Named the current file deliberately. A bare percentage on a four-hundred-file
 * import reads as a frozen bar; a filename changing every second reads as
 * progress, and it is also the honest answer to "what is it doing right now".
 *
 * Every state now sits inside one rounded, softly tinted card rather than an
 * edge-to-edge flat rectangle — the same shape language as [GlassCard], just
 * always tinted since a banner is never neutral about what it's reporting.
 */
@Composable
fun TaskBanner(
    state: TaskState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        TaskState.Idle -> Unit

        is TaskState.Running -> Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dewey.radii.medium))
                .background(Dewey.colors.accentSoft)
                .padding(Dewey.spacing.row),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.total > 0) "Reading ${state.completed} of ${state.total}"
                    else "Looking through the folder",
                    style = Dewey.type.Label,
                    color = Dewey.colors.accent,
                )
                SecondaryAction(label = "Stop", onClick = onCancel)
            }

            state.currentItem?.let { current ->
                Text(
                    text = current,
                    style = Dewey.type.Mono,
                    color = Dewey.colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.padding(top = Dewey.spacing.tight),
                )
            }

            ProgressRule(
                fraction = state.fraction,
                indeterminate = state.total <= 0,
                modifier = Modifier.padding(top = Dewey.spacing.row),
            )
        }

        is TaskState.Finished -> Banner(
            icon = if (state.failed == 0) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            text = when {
                state.failed == 0 -> "Shelved ${state.processed} documents"
                else -> "Shelved ${state.processed}, couldn't read ${state.failed}"
            },
            background = if (state.failed == 0) Dewey.colors.accentSoft else Dewey.colors.attentionSoft,
            foreground = if (state.failed == 0) Dewey.colors.accent else Dewey.colors.attention,
            modifier = modifier,
        )

        is TaskState.Sorted -> Banner(
            icon = if (state.review == 0 && state.failed == 0) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            text = sortedMessage(state),
            background = if (state.review == 0 && state.failed == 0) Dewey.colors.accentSoft else Dewey.colors.attentionSoft,
            foreground = if (state.review == 0 && state.failed == 0) Dewey.colors.accent else Dewey.colors.attention,
            modifier = modifier,
        )

        is TaskState.Restored -> Banner(
            icon = if (state.failed == 0) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            text = restoredMessage(state),
            background = if (state.failed == 0) Dewey.colors.accentSoft else Dewey.colors.attentionSoft,
            foreground = if (state.failed == 0) Dewey.colors.accent else Dewey.colors.attention,
            modifier = modifier,
        )

        is TaskState.Failed -> Banner(
            icon = Icons.Rounded.ErrorOutline,
            text = state.message,
            background = Dewey.colors.attentionSoft,
            foreground = Dewey.colors.danger,
            modifier = modifier,
        )

        TaskState.Cancelled -> Banner(
            icon = Icons.Rounded.PauseCircle,
            text = "Stopped. What was read is kept.",
            background = Dewey.colors.paperSunken,
            foreground = Dewey.colors.inkMuted,
            modifier = modifier,
        )
    }
}

/**
 * "Filed 108 documents into 7 folders. 3 need a look."
 *
 * This copy is what the demo rests on, so it says what actually happened
 * rather than reusing the indexing banner's "shelved" language, which does
 * not fit a job that also leaves some documents where they were.
 */
internal fun sortedMessage(state: TaskState.Sorted): String {
    val filed = when {
        state.moved > 0 ->
            "Filed ${state.moved.withNoun("document")} into ${state.folders.withNoun("folder")}."

        // "Nothing new to file" is true but reads like nothing happened, which
        // is wrong when a run has just recognised a folder full of documents
        // somebody else — or an earlier run — had already filed.
        state.recognised > 0 ->
            "Nothing new to file; ${state.recognised.withNoun("document")} already filed."

        else -> "Nothing new to file."
    }
    val review = when {
        state.review <= 0 -> null
        state.review == 1 -> "1 needs a look."
        else -> "${state.review} need a look."
    }
    val failed = when {
        state.failed <= 0 -> null
        state.failed == 1 -> "1 couldn't be filed."
        else -> "${state.failed} couldn't be filed."
    }
    return listOfNotNull(filed, review, failed).joinToString(" ")
}

private fun restoredMessage(state: TaskState.Restored): String {
    val restored = if (state.restored == 0) {
        "Nothing to put back."
    } else {
        "Put ${state.restored.withNoun("document")} back."
    }
    val failed = when {
        state.failed <= 0 -> null
        state.failed == 1 -> "1 couldn't be restored."
        else -> "${state.failed} couldn't be restored."
    }
    return listOfNotNull(restored, failed).joinToString(" ")
}

/** "1 document", "7 folders" — the count a person would actually write. */
private fun Int.withNoun(noun: String): String = if (this == 1) "1 $noun" else "$this ${noun}s"

@Composable
private fun Banner(
    icon: ImageVector,
    text: String,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dewey.radii.medium))
            .background(background)
            .padding(Dewey.spacing.row),
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
        Text(text = text, style = Dewey.type.Label, color = foreground)
    }
}

/**
 * Progress as a rounded rule that fills, rather than a Material bar.
 *
 * The rule is already the interface's main structural device, so progress
 * reuses it instead of importing a different visual language for one component.
 */
@Composable
private fun ProgressRule(
    fraction: Float,
    indeterminate: Boolean,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = if (indeterminate) 0.08f else fraction.coerceIn(0f, 1f),
        label = "progress",
    )
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(shape)
            .background(Dewey.colors.rule),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(3.dp)
                .clip(shape)
                .background(Dewey.colors.accent)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, widthDp = 390)
@Composable
private fun TaskBannerPreview() {
    DeweyTheme {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            TaskBanner(TaskState.Running(37, 312, "Scan_20240312_004.pdf"), {})
            TaskBanner(TaskState.Finished(310, 2), {})
            TaskBanner(TaskState.Sorted(moved = 108, review = 3, folders = 7, failed = 0), {})
            TaskBanner(TaskState.Sorted(moved = 0, review = 0, folders = 0, failed = 0), {})
            TaskBanner(TaskState.Restored(restored = 108, failed = 0), {})
            TaskBanner(TaskState.Cancelled, {})
        }
    }
}
