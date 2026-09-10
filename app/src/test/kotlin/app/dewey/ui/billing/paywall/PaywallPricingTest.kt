package app.dewey.ui.billing.paywall

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The paywall's arithmetic and wording, tested as plain functions — no
 * offering, no `Package`, no Activity. See [LibrarianBilling]'s doc for why
 * that split exists.
 */
class PaywallPricingTest {

    @Test
    fun `annual saving over paying monthly is a whole percent, rounded down`() {
        // $4.99 a month for a year is $59.88; $29.99 a year is exactly 49.9…% of that.
        val percent = annualSavingsPercent(
            monthlyAmountMicros = 4_990_000L,
            monthlyCurrencyCode = "USD",
            annualAmountMicros = 29_990_000L,
            annualCurrencyCode = "USD",
        )

        assertThat(percent).isEqualTo(49)
    }

    @Test
    fun `a saving that rounds below 10 percent is not worth naming`() {
        // 12 * 4.00 = 48.00; 44.00 is an 8.3% saving.
        val percent = annualSavingsPercent(
            monthlyAmountMicros = 4_000_000L,
            monthlyCurrencyCode = "USD",
            annualAmountMicros = 44_000_000L,
            annualCurrencyCode = "USD",
        )

        assertThat(percent).isNull()
    }

    @Test
    fun `a saving of exactly the 10 percent floor is still shown`() {
        // 12 * 10.00 = 120.00; 108.00 is exactly a 10% saving.
        val percent = annualSavingsPercent(
            monthlyAmountMicros = 10_000_000L,
            monthlyCurrencyCode = "USD",
            annualAmountMicros = 108_000_000L,
            annualCurrencyCode = "USD",
        )

        assertThat(percent).isEqualTo(10)
    }

    @Test
    fun `different currencies never produce a saving, however the numbers compare`() {
        val percent = annualSavingsPercent(
            monthlyAmountMicros = 4_990_000L,
            monthlyCurrencyCode = "USD",
            annualAmountMicros = 29_990_000L,
            annualCurrencyCode = "EUR",
        )

        assertThat(percent).isNull()
    }

    @Test
    fun `a currency code compares case-insensitively`() {
        val percent = annualSavingsPercent(
            monthlyAmountMicros = 4_990_000L,
            monthlyCurrencyCode = "usd",
            annualAmountMicros = 29_990_000L,
            annualCurrencyCode = "USD",
        )

        assertThat(percent).isEqualTo(49)
    }

    @Test
    fun `no monthly price to compare against produces no saving`() {
        val percent = annualSavingsPercent(
            monthlyAmountMicros = 0L,
            monthlyCurrencyCode = "USD",
            annualAmountMicros = 29_990_000L,
            annualCurrencyCode = "USD",
        )

        assertThat(percent).isNull()
    }

    @Test
    fun `the annual badge names the saving when there is one`() {
        assertThat(annualBadgeLabel(58)).isEqualTo("Best value · Save 58%")
    }

    @Test
    fun `the annual badge stays honest with nothing to compare against`() {
        assertThat(annualBadgeLabel(null)).isEqualTo("Best value")
    }

    @Test
    fun `the annual plan is preselected when there is one`() {
        val plans = listOf(
            PaywallPlan(id = "monthly", kind = PaywallPlanKind.MONTHLY, priceFormatted = "$4.99", periodLabel = "month"),
            PaywallPlan(id = "annual", kind = PaywallPlanKind.ANNUAL, priceFormatted = "$29.99", periodLabel = "year"),
        )

        assertThat(preselectedPlanId(plans)).isEqualTo("annual")
    }

    @Test
    fun `the first plan is preselected when there is no annual one to prefer`() {
        val plans = listOf(
            PaywallPlan(id = "lifetime", kind = PaywallPlanKind.OTHER, priceFormatted = "$99.99", periodLabel = "period"),
            PaywallPlan(id = "weekly", kind = PaywallPlanKind.OTHER, priceFormatted = "$1.99", periodLabel = "week"),
        )

        assertThat(preselectedPlanId(plans)).isEqualTo("lifetime")
    }

    @Test
    fun `nothing is preselected when there is nothing to select`() {
        assertThat(preselectedPlanId(emptyList())).isNull()
    }

    @Test
    fun `the cta names the trial only when there is one`() {
        assertThat(ctaLabel(hasFreeTrial = true)).isEqualTo("Start free trial")
        assertThat(ctaLabel(hasFreeTrial = false)).isEqualTo("Continue")
    }

    @Test
    fun `the renewal line names the plan's own price and period`() {
        assertThat(renewalWording("$29.99", "year"))
            .isEqualTo("Renews at $29.99/year. Cancel anytime.")
    }

    @Test
    fun `billing cadence reads as an adverb for every period this app expects`() {
        assertThat(billingCadenceWording("year")).isEqualTo("yearly")
        assertThat(billingCadenceWording("month")).isEqualTo("monthly")
        assertThat(billingCadenceWording("week")).isEqualTo("weekly")
        assertThat(billingCadenceWording("day")).isEqualTo("daily")
        assertThat(billingCadenceWording("period")).isEqualTo("every period")
    }

    @Test
    fun `the per-month line pairs the monthly price with how it's actually billed`() {
        assertThat(perMonthBilledLine("$2.50", "year")).isEqualTo("$2.50/month, billed yearly")
    }

    @Test
    fun `plan titles are fixed for the two headline kinds`() {
        assertThat(planTitle(PaywallPlanKind.ANNUAL, displayName = null)).isEqualTo("Annual")
        assertThat(planTitle(PaywallPlanKind.MONTHLY, displayName = null)).isEqualTo("Monthly")
    }

    @Test
    fun `a generic plan's title falls back to its own product name`() {
        assertThat(planTitle(PaywallPlanKind.OTHER, displayName = "Lifetime Access")).isEqualTo("Lifetime Access")
        assertThat(planTitle(PaywallPlanKind.OTHER, displayName = null)).isEqualTo("Plan")
        assertThat(planTitle(PaywallPlanKind.OTHER, displayName = "  ")).isEqualTo("Plan")
    }

    @Test
    fun `an annual plan's price line leads with the per-month equivalent when there is one`() {
        val annual = PaywallPlan(
            id = "annual",
            kind = PaywallPlanKind.ANNUAL,
            priceFormatted = "$29.99",
            periodLabel = "year",
            pricePerMonthFormatted = "$2.50",
        )

        assertThat(planPriceLine(annual)).isEqualTo("$2.50/month, billed yearly")
    }

    @Test
    fun `a plan's price line falls back to its own price and period without a per-month figure`() {
        val monthly = PaywallPlan(id = "monthly", kind = PaywallPlanKind.MONTHLY, priceFormatted = "$4.99", periodLabel = "month")

        assertThat(planPriceLine(monthly)).isEqualTo("$4.99/month")
    }
}
