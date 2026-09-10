package app.dewey.ui.billing.paywall

/** Below this, a savings figure reads as marketing noise rather than a reason to pick annual. */
private const val MIN_SAVINGS_PERCENT_TO_SHOW = 10
private const val MONTHS_PER_YEAR = 12

/**
 * What annual saves over paying monthly for a year, as a whole percent — or
 * null when there is nothing honest to say: no monthly price to compare
 * against, the two priced in different currencies (a discount that isn't
 * one), or a saving that rounds below [MIN_SAVINGS_PERCENT_TO_SHOW].
 *
 * Works from `amountMicros` rather than the store-formatted price strings,
 * which carry rounding and a currency symbol that would turn this into a
 * string comparison instead of a numeric one.
 *
 * Whole-number arithmetic, not floating point: `1 - 108/120` in doubles is
 * 0.0999…, which floors a real 10% saving to 9 and hides the badge. Micros
 * times 100 still fits a Long with room to spare at any realistic price.
 */
fun annualSavingsPercent(
    monthlyAmountMicros: Long,
    monthlyCurrencyCode: String,
    annualAmountMicros: Long,
    annualCurrencyCode: String,
): Int? {
    if (monthlyAmountMicros <= 0 || annualAmountMicros <= 0) return null
    if (!monthlyCurrencyCode.equals(annualCurrencyCode, ignoreCase = true)) return null

    val payingMonthlyForAYear = monthlyAmountMicros * MONTHS_PER_YEAR
    val saved = payingMonthlyForAYear - annualAmountMicros
    if (saved <= 0) return null
    val percent = (saved * 100 / payingMonthlyForAYear).toInt()
    return percent.takeIf { it >= MIN_SAVINGS_PERCENT_TO_SHOW }
}

/** "Best value", with the saving named when [savingsPercent] is worth saying. */
fun annualBadgeLabel(savingsPercent: Int?): String =
    if (savingsPercent != null) "Best value · Save $savingsPercent%" else "Best value"

/** The annual plan when there is one to prefer, otherwise whatever plan came first — never null while [plans] isn't empty. */
fun preselectedPlanId(plans: List<PaywallPlan>): String? =
    plans.firstOrNull { it.kind == PaywallPlanKind.ANNUAL }?.id ?: plans.firstOrNull()?.id

/** "Start free trial" only when the plan actually has one to start; "Continue" otherwise — the button never quotes a price. */
fun ctaLabel(hasFreeTrial: Boolean): String = if (hasFreeTrial) "Start free trial" else "Continue"

/** The honest line under the CTA: what it costs and how often, then that it can be stopped — said once. */
fun renewalWording(priceFormatted: String, periodLabel: String): String =
    "Renews at $priceFormatted/$periodLabel. Cancel anytime."

/** "month" → "monthly", falling back to "every &lt;period&gt;" for anything this app doesn't otherwise expect. */
fun billingCadenceWording(periodLabel: String): String = when (periodLabel) {
    "year" -> "yearly"
    "month" -> "monthly"
    "week" -> "weekly"
    "day" -> "daily"
    else -> "every $periodLabel"
}

/** The annual card's secondary line: its monthly-equivalent price, with what it's actually billed as — "$2.50/month, billed yearly". */
fun perMonthBilledLine(pricePerMonthFormatted: String, periodLabel: String): String =
    "$pricePerMonthFormatted/month, billed ${billingCadenceWording(periodLabel)}"

/** The card heading: fixed for the two headline plans, the product's own name for a generic fallback one. */
fun planTitle(kind: PaywallPlanKind, displayName: String?): String = when (kind) {
    PaywallPlanKind.ANNUAL -> "Annual"
    PaywallPlanKind.MONTHLY -> "Monthly"
    PaywallPlanKind.OTHER -> displayName?.takeIf { it.isNotBlank() } ?: "Plan"
}

/** A plan card's price line — the monthly-equivalent framing for annual, "price/period" for everything else. */
fun planPriceLine(plan: PaywallPlan): String {
    val perMonth = plan.pricePerMonthFormatted
    return if (plan.kind == PaywallPlanKind.ANNUAL && perMonth != null) {
        perMonthBilledLine(perMonth, plan.periodLabel)
    } else {
        "${plan.priceFormatted}/${plan.periodLabel}"
    }
}
