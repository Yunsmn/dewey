package app.dewey.billing

import android.content.Context
import android.util.Log
import app.dewey.BuildConfig
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the person using the app has the paid tier.
 *
 * The boundary the README draws: reading, viewing and searching your own
 * documents is free forever, and the work the app does *for* you — reading four
 * hundred files, deciding what they are and filing them — is the paid part.
 * That is the honest place for a paywall, because it is the part that costs
 * something to build and the part that saves an afternoon.
 *
 * ## No API key
 *
 * A clean clone has no `revenuecat.properties`, so there is no purchase system
 * to ask. Everything is then unlocked rather than everything being locked:
 * a repo someone clones to read the code should run, and locking its best
 * feature behind a purchase that cannot be made would be a worse lie than
 * giving it away. [isConfigured] says which of the two situations you are in,
 * so the UI can tell the truth about it.
 */
class Entitlements(private val context: Context) {

    private val state = MutableStateFlow(!isConfigured)

    /** True when the paid features should be available. */
    val isEntitled: Flow<Boolean> = state.asStateFlow()

    /** Whether a purchase system exists at all in this build. */
    val isConfigured: Boolean get() = Companion.isConfigured

    /**
     * Starts the SDK. Safe to call when there is no key — it does nothing.
     *
     * Called from Application.onCreate because RevenueCat needs to be
     * configured before anything asks it a question, and a paywall opened from
     * a cold start is exactly when that would happen.
     */
    fun start(scope: CoroutineScope) {
        if (!isConfigured) {
            Log.i(TAG, "No RevenueCat key; every feature is unlocked")
            return
        }

        Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
        Purchases.configure(
            PurchasesConfiguration.Builder(context, BuildConfig.REVENUECAT_KEY).build()
        )

        // A listener and an initial read, not one or the other. The listener
        // alone would leave the app locked until something changed, which for
        // somebody who bought it last week is the entire session.
        Purchases.sharedInstance.updatedCustomerInfoListener =
            UpdatedCustomerInfoListener { info -> state.value = info.hasLibrarian() }

        scope.launch {
            try {
                state.value = Purchases.sharedInstance.awaitCustomerInfo().hasLibrarian()
            } catch (e: PurchasesException) {
                // Offline, or the sandbox is unreachable. Staying locked is the
                // right failure: unlocking on an error would make the
                // entitlement meaningless to anyone who turned their network
                // off. The listener will correct it when the SDK catches up.
                Log.w(TAG, "Could not read entitlements: ${e.message}")
            }
        }
    }

    /** Re-reads after a purchase, so the UI does not wait on the listener. */
    suspend fun refresh() {
        if (!isConfigured) return
        try {
            state.value = Purchases.sharedInstance.awaitCustomerInfo().hasLibrarian()
        } catch (e: PurchasesException) {
            Log.w(TAG, "Could not refresh entitlements: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "Entitlements"

        /**
         * The entitlement identifier configured in the RevenueCat dashboard.
         * Must match exactly, or every customer reads as unentitled.
         */
        const val LIBRARIAN = "librarian"

        val isConfigured: Boolean get() = BuildConfig.REVENUECAT_KEY.isNotBlank()

        private fun CustomerInfo.hasLibrarian(): Boolean =
            entitlements[LIBRARIAN]?.isActive == true
    }
}
