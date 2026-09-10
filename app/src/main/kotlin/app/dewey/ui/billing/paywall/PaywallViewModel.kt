package app.dewey.ui.billing.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val OFFERINGS_FAILURE_MESSAGE = "Couldn't load plans. Check your connection and try again."
private const val PURCHASE_FAILURE_MESSAGE = "The purchase didn't go through. Try again."
private const val RESTORE_FAILURE_MESSAGE = "Couldn't restore a purchase. Try again."

/**
 * The paywall's state machine, over [LibrarianBilling] rather than
 * RevenueCat directly — see that interface's doc for why.
 *
 * @param onEntitled called after a purchase or a restore succeeds, so the
 *   host can re-read the entitlement immediately rather than waiting for
 *   RevenueCat's own listener to catch up — the same reasoning as
 *   [app.dewey.billing.Entitlements.refresh], which is what this is in
 *   practice.
 * @param dispatcher where [billing] is called. The main thread, not IO, and
 *   that is load-bearing: starting a purchase shows UI — Google Play's sheet,
 *   or on the Test Store an AlertDialog built on the calling thread — and
 *   from an IO worker the Test Store threw "Can't create handler inside
 *   thread that has not called Looper.prepare()", so every purchase failed.
 *   RevenueCat's calls are asynchronous, so the main thread never waits on
 *   the network. Overridable so a test can hand in an
 *   `UnconfinedTestDispatcher` and read `state.value` straight back out.
 */
class PaywallViewModel(
    private val billing: LibrarianBilling,
    private val onEntitled: suspend () -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

    private val _state = MutableStateFlow(PaywallUiState())
    val state: StateFlow<PaywallUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Fetches the offering fresh — the initial load, and what "Retry" calls after it failed. */
    fun load() {
        _state.value = PaywallUiState(phase = PaywallPhase.Loading)
        viewModelScope.launch(dispatcher) {
            billing.loadPlans().fold(
                onSuccess = { plans ->
                    _state.value = PaywallUiState(
                        phase = PaywallPhase.Ready,
                        plans = plans,
                        selectedPlanId = preselectedPlanId(plans),
                    )
                },
                onFailure = {
                    _state.value = PaywallUiState(phase = PaywallPhase.OfferingsFailed, errorMessage = OFFERINGS_FAILURE_MESSAGE)
                },
            )
        }
    }

    /**
     * The paywall was opened. This ViewModel belongs to the screen underneath
     * and outlives the dialog, so a second opening would otherwise show the
     * last visit's error, or its Success, which closes the dialog at once.
     * Skipped while the first load is still running, so the first opening
     * does not fetch twice.
     */
    fun onOpened() {
        if (_state.value.phase != PaywallPhase.Loading) load()
    }

    /** Switches which plan is highlighted; ignored once a purchase is already in flight or has already succeeded. */
    fun selectPlan(planId: String) {
        if (_state.value.phase != PaywallPhase.Ready) return
        _state.value = _state.value.copy(selectedPlanId = planId)
    }

    fun purchase(activity: Activity) {
        val planId = _state.value.selectedPlanId ?: return
        _state.value = _state.value.copy(phase = PaywallPhase.Purchasing, errorMessage = null)
        viewModelScope.launch(dispatcher) {
            applyOutcome(billing.purchase(activity, planId), PaywallPhase.PurchaseFailed, PURCHASE_FAILURE_MESSAGE)
        }
    }

    fun restore() {
        _state.value = _state.value.copy(phase = PaywallPhase.Restoring, errorMessage = null)
        viewModelScope.launch(dispatcher) {
            applyOutcome(billing.restore(), PaywallPhase.RestoreFailed, RESTORE_FAILURE_MESSAGE)
        }
    }

    /**
     * Cancelling goes straight back to [PaywallPhase.Ready] with no message —
     * backing out of a store sheet is not a failure the paywall should ever
     * name. Both callers land here so the two error phases they can produce
     * ([PaywallPhase.PurchaseFailed], [PaywallPhase.RestoreFailed]) stay
     * distinct from one another.
     */
    private suspend fun applyOutcome(outcome: PurchaseOutcome, failurePhase: PaywallPhase, fallbackMessage: String) {
        when (outcome) {
            PurchaseOutcome.Success -> {
                // Success first: refreshing flips the entitlement, and the
                // dialog closes on that flip unless it can already see this
                // was its own purchase — otherwise the confirmation never shows.
                _state.value = _state.value.copy(phase = PaywallPhase.Success, errorMessage = null)
                onEntitled()
            }
            PurchaseOutcome.Cancelled -> _state.value = _state.value.copy(phase = PaywallPhase.Ready, errorMessage = null)
            is PurchaseOutcome.Failed -> _state.value = _state.value.copy(
                phase = failurePhase,
                errorMessage = outcome.message.ifBlank { fallbackMessage },
            )
        }
    }

    companion object {
        fun factory(billing: LibrarianBilling, onEntitled: suspend () -> Unit) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PaywallViewModel(billing, onEntitled) as T
        }
    }
}
