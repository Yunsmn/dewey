package app.dewey.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
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

/** The scan button's height, which the list beneath it must also clear. */
internal val HomeScanFabHeight = 64.dp

/**
 * The one action Home never makes anyone hunt for.
 *
 * Floating above the list rather than sitting in its flow, for the same
 * reason the scan screen's own capture button is large and singular:
 * scanning is what most people open this app to do, and it should stay
 * reachable no matter how far the list underneath has scrolled.
 *
 * Unlike the nav bar's tabs, this keeps its ripple: it is clipped to the pill
 * first, so the press shows inside the button rather than splashing across
 * the list, and a primary action with no press feedback reads as dead.
 */
@Composable
internal fun HomeScanFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(HomeScanFabHeight / 2)

    Row(
        modifier = modifier
            .shadow(14.dp, shape, clip = false)
            .clip(shape)
            .background(Dewey.colors.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .height(HomeScanFabHeight)
            .padding(horizontal = Dewey.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight),
    ) {
        Icon(
            imageVector = Icons.Rounded.DocumentScanner,
            contentDescription = null,
            tint = Dewey.colors.onAccent,
        )
        Text("Scan", style = Dewey.type.Button, color = Dewey.colors.onAccent)
    }
}
