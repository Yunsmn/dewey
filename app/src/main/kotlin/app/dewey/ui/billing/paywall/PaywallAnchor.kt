package app.dewey.ui.billing.paywall

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private val MONTHS_IN_A_YEAR = BigDecimal(12)

/**
 * What twelve months of the monthly plan cost, as a price: the crossed-out
 * figure the annual card sets itself against.
 *
 * Deliberately this comparison and not an invented "original price". A
 * struck-through reference must be a price someone would really pay — store
 * policy and consumer law both treat a made-up one as a fake discount — and
 * paying monthly for a year is a real choice on this same paywall. So the
 * saving it implies is true, and it follows the dashboard whenever the prices
 * change.
 *
 * Null for a non-positive amount or a currency code [Currency] does not know,
 * rather than a figure in the wrong currency.
 */
fun yearOfMonthlyFormatted(monthlyAmountMicros: Long, currencyCode: String, locale: Locale): String? {
    if (monthlyAmountMicros <= 0) return null
    val currency = runCatching { Currency.getInstance(currencyCode.uppercase(Locale.ROOT)) }.getOrNull() ?: return null
    val digits = currency.defaultFractionDigits.coerceAtLeast(0)
    val format = NumberFormat.getCurrencyInstance(locale).apply {
        this.currency = currency
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }
    val yearly = BigDecimal.valueOf(monthlyAmountMicros).multiply(MONTHS_IN_A_YEAR).movePointLeft(6)
    return format.format(yearly)
}
