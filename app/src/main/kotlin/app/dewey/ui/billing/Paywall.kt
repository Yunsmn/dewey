package app.dewey.ui.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.billing.Entitlements
import app.dewey.ui.billing.paywall.PaywallPhase
import app.dewey.ui.billing.paywall.PaywallScreen
import app.dewey.ui.billing.paywall.PaywallViewModel
import app.dewey.ui.billing.paywall.RevenueCatLibrarianBilling
import kotlinx.coroutines.delay

/**
 * The paywall, hand-built rather than RevenueCat's own template.
 *
 * The offering, the prices and which plan is worth recommending still come
 * from RevenueCat at runtime — that half of the point of paying for a
 * purchases SDK hasn't changed, see [app.dewey.ui.billing.paywall.RevenueCatLibrarianBilling] and
 * [app.dewey.ui.billing.paywall.annualSavingsPercent]. What changed is who
 * draws it: the demo video this app exists for shows this screen, and
 * RevenueCat's default template carries its own branding rather than this
 * app's — see `ui/billing/paywall/` for the screen itself, the state machine
 * behind it, and the pricing arithmetic, kept apart from RevenueCat's own
 * types so all three are tested without a store.
 *
 * Shown as a dialog over the library rather than as its own screen. The
 * moment that earns a purchase is the one where somebody has just pointed
 * the app at four hundred files and pressed Sort — putting a full-screen
 * route between them and their own library at that moment reads as a toll
 * gate.
 *
 * @param onDismiss called whether the purchase succeeded or the dialog was
 *   closed. The entitlement flow is what decides the outcome, not this: a
 *   successful purchase arrives through
 *   [app.dewey.billing.Entitlements.isEntitled] like any other change, so
 *   there is no success path here to get out of step with it.
 */
@Composable
fun LibrarianPaywall(
    entitlements: Entitlements,
    onDismiss: () -> Unit,
) {
    // A build with no API key has no offerings to fetch and would show an error
    // where the paywall should be. It also has nothing to sell — see
    // Entitlements, where an unconfigured build unlocks everything.
    if (!entitlements.isConfigured) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val billing = remember { RevenueCatLibrarianBilling() }
    val viewModel: PaywallViewModel = viewModel(
        factory = PaywallViewModel.factory(billing = billing, onEntitled = entitlements::refresh),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Read separately from the paywall's own state: a purchase that lands
    // elsewhere — another device, the listener catching up — should close
    // this the same way finishing one here does.
    val entitled by entitlements.isEntitled.collectAsStateWithLifecycle(initialValue = false)

    // The ViewModel is scoped to the screen that opened the paywall, not to
    // the dialog, so it outlives a close. Each opening starts fresh — see
    // onOpened — rather than showing the last visit's error or success.
    LaunchedEffect(Unit) { viewModel.onOpened() }

    // Only for a purchase that lands elsewhere. While this paywall's own
    // purchase or restore is in flight, or showing its confirmation, the
    // entitlement flips as a consequence of it, and the confirmation below
    // owns when the dialog closes.
    LaunchedEffect(entitled, state.phase) {
        if (entitled && state.phase !in OWN_TRANSACTION_PHASES) onDismiss()
    }
    LaunchedEffect(state.phase) {
        if (state.phase == PaywallPhase.Success) {
            delay(SUCCESS_CONFIRMATION_MILLIS)
            onDismiss()
        }
    }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    Dialog(
        onDismissRequest = onDismiss,
        // Edge to edge: the dialog window otherwise keeps grey system-bar
        // strips above and below the paywall. PaywallScreen pads for the
        // insets itself.
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        PaywallScreen(
            state = state,
            onSelectPlan = viewModel::selectPlan,
            onPurchase = { activity?.let(viewModel::purchase) },
            onRestore = viewModel::restore,
            onRetry = viewModel::load,
            onClose = onDismiss,
        )
    }
}

/** How long "Welcome to Librarian" stays up before the dialog closes itself. */
private const val SUCCESS_CONFIRMATION_MILLIS = 1200L

/** Phases in which an entitlement change is this paywall's own doing, not news from elsewhere. */
private val OWN_TRANSACTION_PHASES = setOf(PaywallPhase.Purchasing, PaywallPhase.Restoring, PaywallPhase.Success)

/** Unwraps a [Context] to the [Activity] hosting it, or null if there isn't one — mirrors `ScanScreen`'s. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
