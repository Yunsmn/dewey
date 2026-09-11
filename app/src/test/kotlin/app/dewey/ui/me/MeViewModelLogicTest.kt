package app.dewey.ui.me

import app.dewey.ui.billing.paywall.PurchaseOutcome
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [restoreMessageFor] is the only pure logic [MeViewModel] adds on top of
 * wiring existing flows together — the rest is combining state that already
 * has its own tests elsewhere ([app.dewey.assistant.AssistantQuota],
 * [app.dewey.data.settings.AppSettings]) or talks to Android/RevenueCat
 * directly, the way `PaywallViewModel` leaves the real SDK to
 * `RevenueCatLibrarianBilling` rather than testing it in a JVM unit test.
 */
class MeViewModelLogicTest {

    @Test
    fun `a successful restore has a message worth showing`() {
        assertThat(restoreMessageFor(PurchaseOutcome.Success)).isEqualTo("Purchases restored.")
    }

    @Test
    fun `backing out of the store sheet says nothing`() {
        assertThat(restoreMessageFor(PurchaseOutcome.Cancelled)).isNull()
    }

    @Test
    fun `a failed restore surfaces its own message`() {
        val outcome = PurchaseOutcome.Failed("Couldn't restore a purchase. Try again.")

        assertThat(restoreMessageFor(outcome)).isEqualTo("Couldn't restore a purchase. Try again.")
    }
}
