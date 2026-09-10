package app.dewey.ui.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dewey.billing.Entitlements
import app.dewey.ui.components.IconTile
import app.dewey.ui.components.NavBarClearance
import app.dewey.ui.components.PrimaryAction
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.Hue

/**
 * Shows [content] to Librarian subscribers, and what it would be to everyone else.
 *
 * For a whole tab that is paid — only the scanner and the PDF tools are free.
 * The locked state names the feature and offers the purchase in one tap rather
 * than showing an empty screen: a tab that opens onto nothing reads as broken,
 * while one that says what it holds reads as an offer.
 *
 * The paywall is composed beside both branches, not inside the locked one.
 * Buying flips the entitlement, which swaps the locked panel for [content];
 * a paywall living inside that panel left composition in the same instant,
 * so its "Welcome to Librarian" confirmation never showed — found on the
 * emulator, where the dialog window closed a tenth of a second after the
 * entitlement arrived. Whether it is open is saveable for the same reason a
 * rotation or a theme change should not close it halfway through a purchase.
 */
@Composable
fun LibrarianGate(
    entitlements: Entitlements,
    icon: ImageVector,
    hue: Hue,
    title: String,
    blurb: String,
    content: @Composable () -> Unit,
) {
    // Starts from what an unconfigured build reports, so a clone with no
    // RevenueCat key never flashes the locked state before unlocking.
    val entitled by entitlements.isEntitled.collectAsStateWithLifecycle(initialValue = !entitlements.isConfigured)
    var showPaywall by rememberSaveable { mutableStateOf(false) }

    if (showPaywall) {
        LibrarianPaywall(entitlements = entitlements, onDismiss = { showPaywall = false })
    }

    if (entitled) {
        content()
    } else {
        LockedPanel(icon = icon, hue = hue, title = title, blurb = blurb, onUnlock = { showPaywall = true })
    }
}

@Composable
private fun LockedPanel(icon: ImageVector, hue: Hue, title: String, blurb: String, onUnlock: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = Dewey.spacing.gutter, end = Dewey.spacing.gutter, bottom = NavBarClearance),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconTile(icon = icon, hue = hue, size = 88.dp)
        Spacer(Modifier.height(Dewey.spacing.gutter))
        Text(title, style = Dewey.type.Display, color = Dewey.colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Dewey.spacing.tight))
        Text(blurb, style = Dewey.type.Body, color = Dewey.colors.inkMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Dewey.spacing.block))
        PrimaryAction(label = "Unlock Librarian", onClick = onUnlock)
    }
}
