package app.dewey.ui.billing.paywall

/** Where one open paywall has got to. */
sealed interface PaywallPhase {
    data object Loading : PaywallPhase

    data object OfferingsFailed : PaywallPhase

    /** Plans loaded, nothing in flight — the person is looking at the cards. */
    data object Ready : PaywallPhase

    data object Purchasing : PaywallPhase

    data object Restoring : PaywallPhase

    data object PurchaseFailed : PaywallPhase

    data object RestoreFailed : PaywallPhase

    data object Success : PaywallPhase
}

/**
 * @param plans empty until [PaywallPhase.Loading] finishes; kept afterwards
 *   even through a failed purchase or restore, so retrying one doesn't mean
 *   fetching offerings again.
 * @param selectedPlanId which of [plans] the person has chosen — the annual
 *   plan to start; see [preselectedPlanId].
 * @param errorMessage set alongside [PaywallPhase.OfferingsFailed],
 *   [PaywallPhase.PurchaseFailed] and [PaywallPhase.RestoreFailed]; always a
 *   sentence a person can read, never a raw exception.
 */
data class PaywallUiState(
    val phase: PaywallPhase = PaywallPhase.Loading,
    val plans: List<PaywallPlan> = emptyList(),
    val selectedPlanId: String? = null,
    val errorMessage: String? = null,
) {
    val selectedPlan: PaywallPlan? get() = plans.firstOrNull { it.id == selectedPlanId }
}
