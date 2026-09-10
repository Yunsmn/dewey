package app.dewey.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.dewey.ui.theme.Dewey

/**
 * The masthead: what plan this is, a way into the assistant, and the
 * screen's own title.
 *
 * The pill and the button never disable themselves for someone without
 * Librarian - they stay tappable and answer with the paywall, the same
 * choice LibraryViewModel.onSort makes for the sort button. A control that
 * looks dead teaches nothing; one that answers when pressed does.
 */
@Composable
internal fun HomeTopBar(
    isEntitled: Boolean,
    onPlanPillClick: () -> Unit,
    onAssistantClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlanPill(isEntitled = isEntitled, onClick = onPlanPillClick)
            AssistantButton(isEntitled = isEntitled, onClick = onAssistantClick)
        }
        Spacer(Modifier.height(Dewey.spacing.gutter))
        Text("Home", style = Dewey.type.Display, color = Dewey.colors.ink)
        Spacer(Modifier.height(Dewey.spacing.tight))
        Text(
            text = "Scan something, reach for a tool, or pick up where you left off.",
            style = Dewey.type.Body,
            color = Dewey.colors.inkMuted,
        )
    }
}

@Composable
private fun PlanPill(isEntitled: Boolean, onClick: () -> Unit) {
    val hue = Dewey.colors.hues.assistant

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(hue.soft)
            // Clipped first, so the ripple stays inside the pill.
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Dewey.spacing.row, vertical = Dewey.spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight),
    ) {
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = null,
            tint = hue.strong,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = if (isEntitled) "Librarian" else "Upgrade",
            style = Dewey.type.Label,
            color = hue.strong,
        )
    }
}

@Composable
private fun AssistantButton(isEntitled: Boolean, onClick: () -> Unit) {
    val hue = Dewey.colors.hues.assistant

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(hue.soft)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = if (isEntitled) "Ask the assistant" else "Unlock the assistant",
            tint = hue.strong,
            modifier = Modifier.size(22.dp),
        )
    }
}
