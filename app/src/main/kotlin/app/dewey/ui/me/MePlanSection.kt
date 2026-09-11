package app.dewey.ui.me

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dewey.ui.components.GlassCard
import app.dewey.ui.components.IconTile
import app.dewey.ui.components.PrimaryAction
import app.dewey.ui.components.SecondaryAction
import app.dewey.ui.theme.Dewey

/**
 * Free or Librarian, and the purchase actions that go with each.
 *
 * "Restore purchases" shows for both plans whenever a purchase system exists
 * at all — a reinstall or a new phone needs it regardless of what this
 * install currently thinks it owns. "Manage subscription" only means anything
 * once there is a subscription, so it needs both [isEntitled] and a
 * non-null [managementUrl] — see [MeViewModel.fetchManagementUrl] for why
 * that can be null even for a subscriber.
 */
@Composable
fun MePlanSection(
    isEntitled: Boolean,
    hasPurchases: Boolean,
    isRestoring: Boolean,
    managementUrl: Uri?,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
    onManageSubscription: (Uri) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), padding = Dewey.spacing.gutter) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Star, Dewey.colors.hues.assistant, size = 52.dp)
            Spacer(Modifier.width(Dewey.spacing.row))
            Column {
                Text(if (isEntitled) "Librarian" else "Free", style = Dewey.type.Title, color = Dewey.colors.ink)
                Text(text = planBlurb(isEntitled, hasPurchases), style = Dewey.type.Meta, color = Dewey.colors.inkMuted)
            }
        }

        if (!isEntitled && hasPurchases) {
            Spacer(Modifier.height(Dewey.spacing.gutter))
            PrimaryAction(label = "Unlock Librarian", onClick = onUpgrade)
        }

        if (hasPurchases) {
            Spacer(Modifier.height(Dewey.spacing.row))
            SecondaryAction(
                label = if (isRestoring) "Restoring…" else "Restore purchases",
                onClick = onRestore,
                icon = Icons.Rounded.Restore,
            )
            if (isEntitled && managementUrl != null) {
                Spacer(Modifier.height(Dewey.spacing.tight))
                SecondaryAction(
                    label = "Manage subscription",
                    onClick = { onManageSubscription(managementUrl) },
                    icon = Icons.Rounded.CreditCard,
                )
            }
        }
    }
}

private fun planBlurb(isEntitled: Boolean, hasPurchases: Boolean): String = when {
    !hasPurchases -> "This build has no purchase system, so everything is unlocked."
    isEntitled -> "Sorting, your documents, bills, notes and the assistant are all yours."
    else -> "The scanner and PDF tools are free. Librarian adds your documents, bills, notes and the assistant."
}
