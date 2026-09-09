package app.dewey.ui.billing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.dewey.billing.Entitlements
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialog
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialogOptions

/**
 * The paywall, as RevenueCat renders it.
 *
 * Deliberately not hand-built. The offering, the prices and the copy are
 * configured in the RevenueCat dashboard and fetched at runtime, which is the
 * point of paying for a purchases SDK: changing what the paid tier costs should
 * not need a new build, and on a Test Store there are no real prices to
 * hard-code anyway.
 *
 * Shown as a dialog over the library rather than as its own screen. The moment
 * that earns a purchase is the one where somebody has just pointed the app at
 * four hundred files and pressed Sort — putting a full-screen route between
 * them and their own library at that moment reads as a toll gate.
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

    PaywallDialog(
        PaywallDialogOptions.Builder()
            .setDismissRequest(onDismiss)
            .setShouldDisplayDismissButton(true)
            // Asked of the customer info RevenueCat already holds, so the
            // dialog closes itself the instant a purchase lands rather than
            // waiting for our own listener to come round.
            .setShouldDisplayBlock { info: CustomerInfo ->
                info.entitlements[Entitlements.LIBRARIAN]?.isActive != true
            }
            .build()
    )
}
