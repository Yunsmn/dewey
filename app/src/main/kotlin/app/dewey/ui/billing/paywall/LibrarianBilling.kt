package app.dewey.ui.billing.paywall

import android.app.Activity

/**
 * One purchasable plan, already translated from whatever RevenueCat handed
 * back into the words and numbers the paywall draws — a screen or a test
 * never touches a `Package` or a `StoreProduct` directly. See
 * [RevenueCatLibrarianBilling] for where that translation happens.
 *
 * @param id the identifier RevenueCat's dashboard gave the package. Opaque
 *   to everything above this layer; passed back to
 *   [LibrarianBilling.purchase] unchanged.
 * @param priceFormatted the plan's own price, store-formatted — "$29.99".
 * @param periodLabel lowercase and singular: "month", "year", "week", "day".
 * @param pricePerMonthFormatted the per-month equivalent, set only on a plan
 *   billed less often than monthly — an annual plan showing "$2.50".
 * @param savingsPercent set only on the annual plan, and only when it is
 *   worth saying out loud — see [annualSavingsPercent].
 * @param hasFreeTrial whether the plan's default purchase option starts
 *   with a free phase. Always false on the Test Store, which cannot
 *   simulate one.
 * @param displayName the product's own name, set only for a [PaywallPlanKind.OTHER]
 *   plan drawn from the generic fallback list — the annual and monthly cards
 *   have their own fixed titles instead.
 */
data class PaywallPlan(
    val id: String,
    val kind: PaywallPlanKind,
    val priceFormatted: String,
    val periodLabel: String,
    val pricePerMonthFormatted: String? = null,
    val savingsPercent: Int? = null,
    val hasFreeTrial: Boolean = false,
    val displayName: String? = null,
)

/** Which of the two headline plans this is, or [OTHER] for the generic fallback list. */
enum class PaywallPlanKind { MONTHLY, ANNUAL, OTHER }

/** What came back from a purchase or a restore attempt. */
sealed interface PurchaseOutcome {
    data object Success : PurchaseOutcome

    /** The person backed out of the store sheet — not a failure worth showing. */
    data object Cancelled : PurchaseOutcome

    /** @param message already a sentence a person can read, never a raw exception. */
    data class Failed(val message: String) : PurchaseOutcome
}

/**
 * What the paywall needs from a purchases SDK, and nothing more.
 *
 * [PaywallViewModel] depends on this rather than on RevenueCat directly, so
 * its state machine — loading, selecting a plan, buying, restoring, and
 * every way each of those can fail or be cancelled — is tested against a
 * fake that returns exactly what a test asks it to, with no store, no
 * network and no Activity. [RevenueCatLibrarianBilling] is the real
 * implementation this app runs with.
 */
interface LibrarianBilling {
    /** Reads the dashboard's current offering and turns it into [PaywallPlan]s. */
    suspend fun loadPlans(): Result<List<PaywallPlan>>

    /** @param activity required by the underlying billing flow to show its own UI. */
    suspend fun purchase(activity: Activity, planId: String): PurchaseOutcome

    suspend fun restore(): PurchaseOutcome
}
