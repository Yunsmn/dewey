package app.dewey.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.billing.Entitlements
import app.dewey.data.recent.RecentFiles
import app.dewey.data.repository.DocumentRepository
import app.dewey.di.AppContainer
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How many recent files Home lists - see [HomeUiState.recent]. */
internal const val HOME_RECENT_LIMIT = 6

/**
 * State for the Home tab.
 *
 * Bills and documents are read regardless of entitlement, and it is
 * [HomeUiState.hasGlance] that decides whether the figures built from them
 * are shown - see its doc. Reading them unconditionally means a purchase
 * completing does not need this flow to restart before the glance appears.
 */
class HomeViewModel(
    private val entitlements: Entitlements,
    private val recentFiles: RecentFiles,
    private val repository: DocumentRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    /** The AI button or the Upgrade pill was tapped without the entitlement to use it. */
    private val paywallRequested = MutableStateFlow(false)

    val state: StateFlow<HomeUiState> = combine(
        entitlements.isEntitled,
        recentFiles.recent,
        repository.observeBills(),
        repository.observeDocuments(),
        paywallRequested,
    ) { entitled, recent, bills, documents, paywall ->
        // Read once per emission, not once per row - same rationale as
        // BillsViewModel.today: a card and the row beside it must agree.
        val asOf = now()
        val today = Instant.ofEpochMilli(asOf).atZone(ZoneId.systemDefault()).toLocalDate()

        HomeUiState(
            isEntitled = entitled,
            recent = recent.take(HOME_RECENT_LIMIT),
            billsGlance = billsGlance(bills, today),
            topCategories = topCategories(documents),
            now = asOf,
            showPaywall = paywall,
        )
    }.stateIn(
        scope = viewModelScope,
        // Same rationale as LibraryViewModel and BillsViewModel: keeps Home
        // alive briefly across a rotation instead of reloading everything
        // from the database on every tab switch.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    /** A locked thing was tapped - see HomeScreen's AI button and plan pill. */
    fun onRequestPaywall() {
        paywallRequested.value = true
    }

    /** The paywall was closed, bought or not - same shape as LibraryViewModel.onPaywallDismissed. */
    fun onPaywallDismissed() {
        paywallRequested.value = false
        // The purchase, if there was one, reaches isEntitled through the
        // SDK's own listener. Re-reading here only removes the wait for it.
        viewModelScope.launch { entitlements.refresh() }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(
                    entitlements = container.entitlements,
                    recentFiles = container.recentFiles,
                    repository = container.documentRepository,
                ) as T
        }
    }
}
