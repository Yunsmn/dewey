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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            text = when {
                state.failed == 0 -> "Shelved ${state.processed} documents"
                else -> "Shelved ${state.processed}, couldn't read ${state.failed}"
            },
            background = if (state.failed == 0) Dewey.colors.accentSoft else Dewey.colors.attentionSoft,
            foreground = if (state.failed == 0) Dewey.colors.accent else Dewey.colors.attention,
            modifier = modifier,
        )

        is TaskState.Failed -> Banner(
            text = state.message,
            background = Dewey.colors.attentionSoft,
            foreground = Dewey.colors.danger,
            modifier = modifier,
        )

        TaskState.Cancelled -> Banner(
            text = "Stopped. What was read is kept.",
            background = Dewey.colors.paperSunken,
            foreground = Dewey.colors.inkMuted,
            modifier = modifier,
        )
    }
}

@Composable
private fun Banner(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = Dewey.type.Label,
        color = foreground,
        modifier = modifier.fillMaxWidth().background(background).padding(Dewey.spacing.row),
    )
}

/**
 * Progress as a rule that fills, rather than a Material bar.
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
    Box(modifier = modifier.fillMaxWidth().height(2.dp).background(Dewey.colors.rule)) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(2.dp)
                .background(Dewey.colors.accent)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F2, widthDp = 390)
@Composable
private fun TaskBannerPreview() {
    DeweyTheme {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            TaskBanner(TaskState.Running(37, 312, "Scan_20240312_004.pdf"), {})
            TaskBanner(TaskState.Finished(310, 2), {})
            TaskBanner(TaskState.Cancelled, {})
        }
    }
}
