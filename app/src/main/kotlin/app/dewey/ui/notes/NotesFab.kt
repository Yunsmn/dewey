package app.dewey.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.dewey.ui.theme.Dewey

/** [NotesFab]'s own height, which the Notes list beneath it must also clear. */
internal val NotesFabHeight = 64.dp

/**
 * The Notes segment's one always-reachable action, floating above the list -
 * same shape and reasoning as [app.dewey.ui.home.HomeScanFab]: writing a note
 * should never require scrolling to find a button first.
 */
@Composable
internal fun NotesFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(NotesFabHeight / 2)

    Row(
        modifier = modifier
            .shadow(14.dp, shape, clip = false)
            .clip(shape)
            .background(Dewey.colors.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .height(NotesFabHeight)
            .padding(horizontal = Dewey.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight),
    ) {
        Icon(imageVector = Icons.Rounded.NoteAdd, contentDescription = null, tint = Dewey.colors.onAccent)
        Text("New note", style = Dewey.type.Button, color = Dewey.colors.onAccent)
    }
}
