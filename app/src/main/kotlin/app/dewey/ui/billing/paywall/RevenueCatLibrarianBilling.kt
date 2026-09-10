package app.dewey.ui.billing.paywall

import android.app.Activity
import android.util.Log
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.models.Period
import com.revenuecat.purchases.models.StoreProduct
import java.util.Locale
import kotlinx.coroutines.CancellationException

private const val TAG = "LibrarianBilling"
private const val PURCHASE_GENERIC_FAILURE = "The purchase didn't go through. Try again."
private const val RESTORE_GENERIC_FAILURE = "Couldn't restore a purchase. Try again."

/**
 * [LibrarianBilling] over the real RevenueCat SDK.
 *
 * Holds the [Package]s the last [loadPlans] call returned, keyed by
 * identifier, so [purchase] can turn the opaque plan id the UI hands back
 * into the object the SDK actually needs. That mapping is the reason
 * [LibrarianBilling.purchase] takes a plan id and not a [Package] — the
 * interface it implements never mentions a RevenueCat type, so a test can
 * implement it without one existing.
 *
 * Every failure becomes a [PurchaseOutcome], not only the SDK's own
 * transaction exception: these run in a ViewModel coroutine with no handler,
 * where anything else thrown — an SDK that was never configured, a store
 * error of some other type — would take the app down at the moment somebody
 * was trying to pay.
 */
class RevenueCatLibrarianBilling : LibrarianBilling {

    private var packagesById: Map<String, Package> = emptyMap()

    override suspend fun loadPlans(): Result<List<PaywallPlan>> = runCatching {
        val offering = Purchases.sharedInstance.awaitOfferings().current
            ?: error("No current offering is configured for this RevenueCat project")
        packagesById = offering.availablePackages.associateBy { it.identifier }
        buildPlans(offering)
    }.onFailure { Log.w(TAG, "Could not load offerings", it) }

    override suspend fun purchase(activity: Activity, planId: String): PurchaseOutcome {
        val rcPackage = packagesById[planId] ?: return PurchaseOutcome.Failed(PURCHASE_GENERIC_FAILURE)
        return try {
            Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, rcPackage).build())
            PurchaseOutcome.Success
        } catch (e: PurchasesTransactionException) {
            if (e.userCancelled) PurchaseOutcome.Cancelled else failed("purchase", e, PURCHASE_GENERIC_FAILURE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed("purchase", e, PURCHASE_GENERIC_FAILURE)
        }
    }

    override suspend fun restore(): PurchaseOutcome = try {
        Purchases.sharedInstance.awaitRestore()
        PurchaseOutcome.Success
    } catch (e: PurchasesTransactionException) {
        if (e.userCancelled) PurchaseOutcome.Cancelled else failed("restore", e, RESTORE_GENERIC_FAILURE)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        failed("restore", e, RESTORE_GENERIC_FAILURE)
    }

    private fun failed(what: String, e: Exception, message: String): PurchaseOutcome {
        Log.w(TAG, "Could not complete $what", e)
        return PurchaseOutcome.Failed(message)
    }
}

/**
 * Builds the two headline plans when the dashboard has both a monthly and
 * an annual package, falling back to a generic list of whatever the
 * offering does have otherwise — see the class doc on [PaywallPlan].
 */
private fun buildPlans(offering: Offering): List<PaywallPlan> {
    val monthly = offering.monthly
    val annual = offering.annual
    if (monthly == null || annual == null) {
        return offering.availablePackages.map(::genericPlan)
    }

    val savings = annualSavingsPercent(
        monthlyAmountMicros = monthly.product.price.amountMicros,
        monthlyCurrencyCode = monthly.product.price.currencyCode,
        annualAmountMicros = annual.product.price.amountMicros,
        annualCurrencyCode = annual.product.price.currencyCode,
    )

    return listOf(annualPlan(annual, savings), monthlyPlan(monthly))
}

private fun annualPlan(annualPackage: Package, savingsPercent: Int?): PaywallPlan {
    val product = annualPackage.product
    return PaywallPlan(
        id = annualPackage.identifier,
        kind = PaywallPlanKind.ANNUAL,
        priceFormatted = product.price.formatted,
        periodLabel = periodLabel(product.period),
        pricePerMonthFormatted = product.pricePerMonth(Locale.getDefault())?.formatted,
        savingsPercent = savingsPercent,
        hasFreeTrial = product.hasFreeTrial(),
    )
}

private fun monthlyPlan(monthlyPackage: Package): PaywallPlan {
    val product = monthlyPackage.product
    return PaywallPlan(
        id = monthlyPackage.identifier,
        kind = PaywallPlanKind.MONTHLY,
        priceFormatted = product.price.formatted,
        periodLabel = periodLabel(product.period),
        hasFreeTrial = product.hasFreeTrial(),
    )
}

private fun genericPlan(rcPackage: Package): PaywallPlan {
    val product = rcPackage.product
    return PaywallPlan(
        id = rcPackage.identifier,
        kind = PaywallPlanKind.OTHER,
        priceFormatted = product.price.formatted,
        periodLabel = periodLabel(product.period),
        hasFreeTrial = product.hasFreeTrial(),
        displayName = product.title,
    )
}

/** The Test Store cannot simulate one, so this is honestly false on it — see the class doc on [LibrarianBilling]'s caller. */
private fun StoreProduct.hasFreeTrial(): Boolean = defaultOption?.freePhase != null

/** RevenueCat's period unit, in the singular noun the paywall's wording functions expect ("month", not "monthly" or "P1M"). */
private fun periodLabel(period: Period?): String = when (period?.unit) {
    Period.Unit.DAY -> "day"
    Period.Unit.WEEK -> "week"
    Period.Unit.MONTH -> "month"
    Period.Unit.YEAR -> "year"
    else -> "period"
}
