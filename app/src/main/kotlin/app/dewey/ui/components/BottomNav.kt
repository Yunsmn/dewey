package app.dewey.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.dewey.ui.theme.Dewey

/**
 * How far content must clear the floating navigation bar.
 *
 * The bar is drawn over the content rather than beside it, so every scrolling
 * screen owes it this much room at the bottom or the last row sits underneath
 * and cannot be read or tapped. Stated once here rather than guessed at three
 * times.
 */
val NavBarClearance = 104.dp

/** One destination in [BottomNav]. */
data class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * The floating bar the whole app is navigated from.
 *
 * It floats over the content rather than sitting under it — inset from all three
 * edges, on the same glass as the cards — so the list scrolls beneath and the
 * app reads as one continuous surface rather than as a page with a footer
 * bolted on.
 *
 * The selected destination is marked by colour and by a filled pill behind it,
 * not by colour alone: about one man in twelve cannot rely on a blue-versus-grey
 * distinction, and "which tab am I on" is not something to make them guess at.
 */
@Composable
fun BottomNav(
    destinations: List<NavDestination>,
    current: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Opaque, and a shadow to sit it above the page. Translucency was
            // tried and is wrong here for a reason worth keeping: a card is
            // translucent over a background that holds still, and what shows
            // through reads as depth. A bar with a list scrolling underneath it
            // shows filenames sliding through the tab labels, which reads as a
            // fault rather than as glass. Compose cannot blur a backdrop below
            // API 31, so opacity plus a shadow is the honest version.
            .shadow(12.dp, shape, clip = false)
            .clip(shape)
            .background(Dewey.colors.glassRaised)
            .border(1.dp, Dewey.colors.glassBorder, shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (destination in destinations) {
            NavItem(
                destination = destination,
                selected = destination.route == current,
                onClick = { onSelect(destination.route) },
            )
        }
    }
}

@Composable
private fun NavItem(
    destination: NavDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) Dewey.colors.accent else Dewey.colors.inkMuted,
        label = "navTint",
    )
    val pill by animateColorAsState(
        targetValue = if (selected) Dewey.colors.accentSoft else Dewey.colors.accentSoft.copy(alpha = 0f),
        label = "navPill",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(pill)
            .clickable(
                // No ripple: it would splash outside the pill and across the
                // glass, which reads as a rendering fault rather than a press.
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = destination.label,
            style = Dewey.type.Micro,
            color = tint,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
