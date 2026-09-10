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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

/**
 * Shows [content] to Librarian subscribers, and what it would be to everyone else.
 *
 * For a whole tab that is paid — only the scanner and the PDF tools are free.
 * The locked state names the feature and offers the purchase in one tap rather
 * than showing an empty screen: a tab that opens onto nothing reads as broken,
 * while one that says what it holds reads as an offer.
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
    if (entitled) {
        content()
        return
    }

    var showPaywall by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showPaywall) {
        LibrarianPaywall(
            entitlements = entitlements,
            onDismiss = {
                showPaywall = false
                // A purchase arrives through the listener anyway; this only removes the wait.
                scope.launch { entitlements.refresh() }
            },
        )
    }

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
        PrimaryAction(label = "Unlock Librarian", onClick = { showPaywall = true })
    }
}
