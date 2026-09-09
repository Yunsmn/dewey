package app.dewey.ui.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * A clone with no RevenueCat key must be a working app, not a locked one.
 *
 * The default matters more than it looks. Every other gate in this codebase
 * fails closed — an unreadable document goes to review, an unverifiable copy is
 * not deleted — and copying that instinct here would be wrong. There is nothing
 * to buy in a build with no purchase system, so locking the app's best feature
 * behind a purchase nobody can make would leave anyone reading the repo with a
 * crippled app and no way to tell why.
 */
class EntitlementDefaultTest {

    @Test
    fun `state is entitled until something says otherwise`() {
        assertThat(LibraryUiState().isEntitled).isTrue()
    }

    @Test
    fun `no paywall is asked for by default`() {
        assertThat(LibraryUiState().showPaywall).isFalse()
    }
}
