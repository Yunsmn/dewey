package app.dewey.ui.billing.paywall

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A stand-in for [RevenueCatLibrarianBilling] that never touches RevenueCat,
 * a store or a network — a test chooses exactly what each call does, the
 * same way [app.dewey.ui.scan.ScanViewModelTest]'s fakes stand in for the
 * scanner.
 */
private class FakeLibrarianBilling : LibrarianBilling {

    var loadCalls = 0
        private set
    var purchaseCalls = 0
        private set
    var restoreCalls = 0
        private set

    /** What the last purchase call was asked to buy, for tests that care. */
    var lastPurchasedPlanId: String? = null
        private set

    var onLoadPlans: suspend () -> Result<List<PaywallPlan>> = { Result.success(emptyList()) }
    var onPurchase: suspend () -> PurchaseOutcome = { PurchaseOutcome.Success }
    var onRestore: suspend () -> PurchaseOutcome = { PurchaseOutcome.Success }

    override suspend fun loadPlans(): Result<List<PaywallPlan>> {
        loadCalls++
        return onLoadPlans()
    }

    override suspend fun purchase(activity: Activity, planId: String): PurchaseOutcome {
        purchaseCalls++
        lastPurchasedPlanId = planId
        return onPurchase()
    }

    override suspend fun restore(): PurchaseOutcome {
        restoreCalls++
        return onRestore()
    }
}

private val MONTHLY = PaywallPlan(id = "monthly", kind = PaywallPlanKind.MONTHLY, priceFormatted = "$4.99", periodLabel = "month")
private val ANNUAL = PaywallPlan(
    id = "annual",
    kind = PaywallPlanKind.ANNUAL,
    priceFormatted = "$29.99",
    periodLabel = "year",
    pricePerMonthFormatted = "$2.50",
    savingsPercent = 49,
)

/**
 * [PaywallViewModel]'s state machine, driven entirely through
 * [FakeLibrarianBilling], which resolves synchronously — every call here
 * runs on an [UnconfinedTestDispatcher] so `state.value` reads the outcome
 * back immediately, the same reasoning as `ScanViewModel`'s own tests.
 */
class PaywallViewModelTest {

    private val activity = mockk<Activity>(relaxed = true)

    private fun viewModel(
        billing: FakeLibrarianBilling,
        onEntitled: suspend () -> Unit = {},
    ) = PaywallViewModel(billing, onEntitled, dispatcher = UnconfinedTestDispatcher())

    @Test
    fun `loads plans and preselects the annual one`() = runTest {
        val billing = FakeLibrarianBilling().apply { onLoadPlans = { Result.success(listOf(MONTHLY, ANNUAL)) } }

        val viewModel = viewModel(billing)

        assertThat(viewModel.state.value.phase).isEqualTo(PaywallPhase.Ready)
        assertThat(viewModel.state.value.plans).containsExactly(MONTHLY, ANNUAL)
        assertThat(viewModel.state.value.selectedPlanId).isEqualTo("annual")
    }

    @Test
    fun `a failed load is reported honestly, not as a blank screen`() = runTest {
        val billing = FakeLibrarianBilling().apply { onLoadPlans = { Result.failure(RuntimeException("boom")) } }

        val viewModel = viewModel(billing)

        assertThat(viewModel.state.value.phase).isEqualTo(PaywallPhase.OfferingsFailed)
        assertThat(viewModel.state.value.errorMessage).isNotNull()
        assertThat(viewModel.state.value.plans).isEmpty()
    }

    @Test
    fun `retry after a failed load fetches again`() = runTest {
        val billing = FakeLibrarianBilling().apply { onLoadPlans = { Result.failure(RuntimeException("boom")) } }
        val viewModel = viewModel(billing)

        billing.onLoadPlans = { Result.success(listOf(MONTHLY, ANNUAL)) }
        viewModel.load()

        assertThat(billing.loadCalls).isEqualTo(2)
        assertThat(viewModel.state.value.phase).isEqualTo(PaywallPhase.Ready)
    }

    @Test
    fun `reopening after a failed purchase starts fresh rather than showing the old error`() = runTest {
        val billing = FakeLibrarianBilling().apply {
            onLoadPlans = { Result.success(listOf(MONTHLY, ANNUAL)) }
            onPurchase = { PurchaseOutcome.Failed("declined") }
        }
        val viewModel = viewModel(billing)
        viewModel.purchase(activity)
        assertThat(viewModel.state.value.phase).isEqualTo(PaywallPhase.PurchaseFailed)

        viewModel.onOpened()

        assertThat(billing.loadCalls).isEqualTo(2)
        assertThat(viewModel.state.value.phase).isEqualTo(PaywallPhase.Ready)
        assertThat(viewModel.state.value.errorMessage).isNull()
    }

    @Test
    fun `opening while the first load is still running does not fetch twice`() = runTest {
        val gate = CompletableDeferred<Result<List<PaywallPlan>>>()
        val billing = FakeLibrarianBilling().apply { onLoadPlans = { gate.await() } }
        val viewModel = viewModel(billing)
        assertThat(viewModel.state.value.phase).isEqualTo(PaywallPhase.Loading)

        viewModel.onOpened()
        gate.complete(Result.success(listOf(MONTHLY, ANNUAL)))

        assertThat(billing.loadCalls).isEqualTo(1)
        assertThat(viewModel.state.value.phase).isEqualTo(PaywallPhase.Ready)
    }

    @Test
    fun `selecting a plan while ready switches which one is highlighted`() = runTest {
        val billing = FakeLibrarianBilling().apply { onLoadPlans = { Result.success(listOf(MONTHLY, ANNUAL)) } }
        val viewModel = viewModel(billing)

        viewModel.selectPlan("monthly")

        assertThat(viewModel.state.value.selectedPlanId).isEqualTo("monthly")
    }

    @Test
    fun `selecting a plan mid-purchase is ignored`() = runTest {
        val purchaseGate = CompletableDeferred<PurchaseOutcome>()
        val billing = FakeLibrarianBilling().apply {
            onLoadPlans = { Result.success(listOf(MONTHLY, ANNUAL)) }
            onPurchase = { purchaseGate.await() }
        }
        val viewModel = viewModel(billing)

        viewModel.purchase(activity)
        assertThat(viewModel.state.value.phase).isEqualTo(PaywallPhase.Purchasing)

        viewModel.selectPlan("monthly")

        assertThat(viewModel.state.value.selectedPlanId).isEqualTo("annual")

        purchaseGate.complete(PurchaseOutcome.Success) // let the launched coroutine finish before the test ends
    }

    @Test
    fun `a successful purchase notifies the host and reaches Success`() = runTest {
        var entitled = false
        val billing = FakeLibrarianBilling().apply {
            onLoadPlans = { Result.success(listOf(MONTHLY, ANNUAL)) }
            onPurchase = { PurchaseOutcome.Success }
        }
        val viewModel = viewModel(billing, onEntitled = { entitled = true })

        viewModel.purchase(activity)

        assertThat(billing.lastPurchasedPlanId).isEqualTo("annual")
        assertThat(entitled).isTrue()
        assertThat(viewModel.state.value.phase).isEqualTo(PaywallPhase.Success)
    }

    @Test
    fun `cancelling a purchase goes back to Ready with no message`() = runTest {
        val billing = FakeLibrarianBilling().apply {
            onLoadPlans = { Result.success(listOf(MONTHLY, ANNUAL)) }
            onPurchase = { PurchaseOutcome.Cancelled }
        }
        val viewModel = viewModel(billing)

        viewModel.purchase(activity)

        assertThat(viewModel.state.value.phase).isEqualTo(PaywallPhase.Ready)
        assertThat(viewModel.state.value.errorMessage).isNull()
        // The plan chosen before backing out is still the one offered next time.
        assertThat(viewModel.state.value.selectedPlanId).isEqualTo("annual")
    }

    @Test
    fun `a failed purchase is reported honestly, never as a raw exception`() = runTest {
        val billing = FakeLibrarianBilling().apply {
            onLoadPlans = { Result.success(listOf(MONTHLY, ANNUAL)) }
            onPurchase = { PurchaseOutcome.Failed("card declined") }
        }
        val viewModel = viewModel(billing)

        viewModel.purchase(activity)

        assertThat(viewModel.state.value.phase).isEqualTo(PaywallPhase.PurchaseFailed)
        assertThat(viewModel.state.value.errorMessage).isEqualTo("card declined")
        // Plans stay put so "Try again" doesn't mean re-fetching the offering.
        assertThat(viewModel.state.value.plans).containsExactly(MONTHLY, ANNUAL)
    }

    @Test
    fun `purchasing with nothing selected does nothing`() = runTest {
        val billing = FakeLibrarianBilling().apply { onLoadPlans = { Result.success(emptyList()) } }
        val viewModel = viewModel(billing)

        viewModel.purchase(activity)

        assertThat(billing.purchaseCalls).isEqualTo(0)
    }

    @Test
    fun `a successful restore notifies the host and reaches Success`() = runTest {
        var entitled = false
        val billing = FakeLibrarianBilling().apply {
            onLoadPlans = { Result.success(listOf(MONTHLY, ANNUAL)) }
            onRestore = { PurchaseOutcome.Success }
        }
        val viewModel = viewModel(billing, onEntitled = { entitled = true })

        viewModel.restore()

        assertThat(entitled).isTrue()
        assertThat(viewModel.state.value.phase).isEqualTo(PaywallPhase.Success)
    }

    @Test
    fun `a failed restore is reported honestly`() = runTest {
        val billing = FakeLibrarianBilling().apply {
            onLoadPlans = { Result.success(listOf(MONTHLY, ANNUAL)) }
            onRestore = { PurchaseOutcome.Failed("nothing to restore") }
        }
        val viewModel = viewModel(billing)

        viewModel.restore()

        assertThat(viewModel.state.value.phase).isEqualTo(PaywallPhase.RestoreFailed)
        assertThat(viewModel.state.value.errorMessage).isEqualTo("nothing to restore")
    }

    @Test
    fun `cancelling a restore goes back to Ready with no message`() = runTest {
        val billing = FakeLibrarianBilling().apply {
            onLoadPlans = { Result.success(listOf(MONTHLY, ANNUAL)) }
            onRestore = { PurchaseOutcome.Cancelled }
        }
        val viewModel = viewModel(billing)

        viewModel.restore()

        assertThat(viewModel.state.value.phase).isEqualTo(PaywallPhase.Ready)
        assertThat(viewModel.state.value.errorMessage).isNull()
    }
}
