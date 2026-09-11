package app.dewey.ui.billing.paywall

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class PaywallAnchorTest {

    @Test
    fun `a year of a 5_99 monthly plan is 71_88`() {
        assertThat(yearOfMonthlyFormatted(5_990_000, "USD", Locale.US)).isEqualTo("$71.88")
    }

    @Test
    fun `whole-number monthly prices keep their cents`() {
        assertThat(yearOfMonthlyFormatted(5_000_000, "USD", Locale.US)).isEqualTo("$60.00")
    }

    @Test
    fun `the currency code is honoured, not the locale's own currency`() {
        val formatted = yearOfMonthlyFormatted(49_990_000, "MAD", Locale.US)

        assertThat(formatted).contains("599.88")
        assertThat(formatted).doesNotContain("$")
    }

    @Test
    fun `a lowercase currency code still resolves`() {
        assertThat(yearOfMonthlyFormatted(5_990_000, "usd", Locale.US)).isEqualTo("$71.88")
    }

    @Test
    fun `an unknown currency or a non-positive amount gives nothing rather than a wrong figure`() {
        assertThat(yearOfMonthlyFormatted(5_990_000, "NOT_A_CURRENCY", Locale.US)).isNull()
        assertThat(yearOfMonthlyFormatted(0, "USD", Locale.US)).isNull()
    }
}
