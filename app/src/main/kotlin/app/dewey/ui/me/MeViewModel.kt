package app.dewey.ui.me

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.assistant.AssistantQuota
import app.dewey.billing.Entitlements
import app.dewey.data.repository.DocumentRepository
import app.dewey.data.settings.AppSettings
import app.dewey.data.settings.ThemeMode
import app.dewey.data.storage.DocumentTreeStore
import app.dewey.di.AppContainer
import app.dewey.ui.billing.paywall.PurchaseOutcome
import app.dewey.ui.billing.paywall.RevenueCatLibrarianBilling
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.awaitCustomerInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Me tab shows. There is no account, so everything here is about this phone. */
data class MeUiState(
    val isEntitled: Boolean,
    /** False in a build with no RevenueCat key, where everything is unlocked and there is nothing to buy. */
    val hasPurchases: Boolean,
    val documents: Int = 0,
    val bills: Int = 0,
    val folders: Int = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val assistantQuestionsLeft: Int = AssistantQuota.DAILY_LIMIT,
    /** Where "Manage subscription" opens to. Null hides the action — see [MeViewModel.fetchManagementUrl]. */
    val managementUrl: Uri? = null,
    val isRestoring: Boolean = false,
)

/** What to tell the person after a restore attempt, or null when nothing needs saying — backing out of the store sheet is not news. */
internal fun restoreMessageFor(outcome: PurchaseOutcome): String? = when (outcome) {
    PurchaseOutcome.Success -> "Purchases restored."
    PurchaseOutcome.Cancelled -> null
    is PurchaseOutcome.Failed -> outcome.message
}

/** The three things about counts and the linked folder that arrive on their own flows. */
private data class Counts(val documents: Int, val bills: Int, val folders: Int)

/** Entitlement, where to manage it, and whether a restore is running right now. */
private data class BillingState(val entitled: Boolean, val managementUrl: Uri?, val isRestoring: Boolean)

/** The two settings the Me tab reads that live outside its own state. */
private data class SettingsState(val themeMode: ThemeMode, val assistantQuestionsLeft: Int)

class MeViewModel(
    repository: DocumentRepository,
    treeStore: DocumentTreeStore,
    private val entitlements: Entitlements,
    private val appSettings: AppSettings,
    assistantQuota: AssistantQuota,
) : ViewModel() {

    private val managementUrl = MutableStateFlow<Uri?>(null)
    private val isRestoring = MutableStateFlow(false)
    private val _restoreEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** One-shot messages for a restore attempt — collected once by the screen and shown as a Toast. */
    val restoreEvents: SharedFlow<String> = _restoreEvents.asSharedFlow()

    private val counts = combine(
        repository.observeCount(),
        repository.observeBills(),
        treeStore.grantedTrees,
    ) { documents, bills, trees -> Counts(documents, bills.size, trees.size) }

    private val billing = combine(
        entitlements.isEntitled,
        managementUrl,
        isRestoring,
    ) { entitled, url, restoring -> BillingState(entitled, url, restoring) }

    private val settings = combine(
        appSettings.themeMode,
        assistantQuota.remainingToday,
    ) { mode, remaining -> SettingsState(mode, remaining) }

    val state: StateFlow<MeUiState> = combine(counts, billing, settings) { c, b, s ->
        MeUiState(
            isEntitled = b.entitled,
            hasPurchases = entitlements.isConfigured,
            documents = c.documents,
            bills = c.bills,
            folders = c.folders,
            themeMode = s.themeMode,
            assistantQuestionsLeft = s.assistantQuestionsLeft,
            managementUrl = b.managementUrl,
            isRestoring = b.isRestoring,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MeUiState(isEntitled = !entitlements.isConfigured, hasPurchases = entitlements.isConfigured),
    )

    init {
        // Re-read whenever the entitlement changes rather than once at
        // startup: a purchase or a restore flips isEntitled, and that is
        // exactly when a management link starts existing.
        viewModelScope.launch {
            entitlements.isEntitled.collect { entitled ->
                managementUrl.value = if (entitled) fetchManagementUrl() else null
            }
        }
    }

    /** Null in a build with no RevenueCat key, and on any failure to reach it — [Purchases.sharedInstance] is never touched in that case. */
    private suspend fun fetchManagementUrl(): Uri? {
        if (!entitlements.isConfigured) return null
        return try {
            Purchases.sharedInstance.awaitCustomerInfo().managementURL
        } catch (e: PurchasesException) {
            Log.w(TAG, "Could not read the subscription management link: ${e.message}")
            null
        }
    }

    fun onPaywallDismissed() {
        viewModelScope.launch { entitlements.refresh() }
    }

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch { appSettings.setThemeMode(mode) }
    }

    /**
     * Restores a purchase made elsewhere — a reinstall, or a new phone.
     * Ignored while one is already running rather than queued, since a second
     * tap while the first is still in flight means the same thing as the first.
     */
    fun onRestorePurchases() {
        if (isRestoring.value) return
        viewModelScope.launch {
            isRestoring.value = true
            val outcome = RevenueCatLibrarianBilling().restore()
            entitlements.refresh()
            restoreMessageFor(outcome)?.let { _restoreEvents.tryEmit(it) }
            isRestoring.value = false
        }
    }

    companion object {
        private const val TAG = "MeViewModel"

        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MeViewModel(
                    repository = container.documentRepository,
                    treeStore = container.documentTreeStore,
                    entitlements = container.entitlements,
                    appSettings = container.appSettings,
                    assistantQuota = container.assistantQuota,
                ) as T
        }
    }
}
