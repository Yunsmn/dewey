package app.dewey.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.data.recent.RecentFile
import app.dewey.data.recent.RecentKind
import app.dewey.di.AppContainer
import app.dewey.domain.model.DocType
import app.dewey.domain.model.Document
import app.dewey.ui.billing.LibrarianPaywall
import app.dewey.ui.components.NavBarClearance
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.tools.ToolDestination
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The free tab: the scanner, the PDF tools, what Dewey made recently, and -
 * once Librarian is unlocked - a glance at bills and categories that live
 * behind the paid tabs. See the brief on where each thing sits; the layout
 * itself is [HomeContent], kept apart from this so a preview never needs a
 * real [AppContainer].
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenTool: (ToolDestination) -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenMe: () -> Unit,
    onOpenFile: (uri: String) -> Unit,
    onOpenBills: () -> Unit,
    onOpenCategory: (label: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Over Home rather than in place of it - same reasoning as
    // LibraryScreen's own paywall: the moment that earns a purchase is the
    // one where somebody has just reached for the thing it gates.
    if (state.showPaywall) {
        LibrarianPaywall(
            entitlements = container.entitlements,
            onDismiss = viewModel::onPaywallDismissed,
        )
    }

    HomeContent(
        state = state,
        onOpenTool = onOpenTool,
        onPlanPillClick = { if (state.isEntitled) onOpenMe() else viewModel.onRequestPaywall() },
        onAssistantClick = { if (state.isEntitled) onOpenAssistant() else viewModel.onRequestPaywall() },
        onOpenFile = onOpenFile,
        onOpenBills = onOpenBills,
        onOpenCategory = onOpenCategory,
        modifier = modifier,
    )
}

/**
 * The stateless layout every preview below renders directly, with no
 * [AppContainer] and no ViewModel in sight.
 */
@Composable
private fun HomeContent(
    state: HomeUiState,
    onOpenTool: (ToolDestination) -> Unit,
    onPlanPillClick: () -> Unit,
    onAssistantClick: () -> Unit,
    onOpenFile: (uri: String) -> Unit,
    onOpenBills: () -> Unit,
    onOpenCategory: (label: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember(state.now) {
        Instant.ofEpochMilli(state.now).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(containerColor = Dewey.colors.paper) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = Dewey.spacing.gutter,
                    end = Dewey.spacing.gutter,
                    top = Dewey.spacing.row,
                    // Clears the scan button too, not only the nav bar: it
                    // floats over the list's last item otherwise, and the
                    // glance cards there could never be scrolled out from under it.
                    bottom = NavBarClearance + HomeScanFabHeight + Dewey.spacing.row,
                ),
                // block, not section: section spacing between every group left
                // Home mostly gaps on a phone screen once it was on a device.
                verticalArrangement = Arrangement.spacedBy(Dewey.spacing.block),
            ) {
                item(key = "topbar") {
                    HomeTopBar(
                        isEntitled = state.isEntitled,
                        onPlanPillClick = onPlanPillClick,
                        onAssistantClick = onAssistantClick,
                    )
                }
                item(key = "tools") {
                    HomeToolsGrid(onOpenTool = onOpenTool)
                }
                item(key = "recent") {
                    HomeRecentSection(recent = state.recent, now = state.now, onOpenFile = onOpenFile)
                }
                if (state.hasGlance) {
                    item(key = "glance") {
                        HomeGlanceSection(
                            billsGlance = state.billsGlance,
                            categories = state.topCategories,
                            today = today,
                            onOpenBills = onOpenBills,
                            onOpenCategory = onOpenCategory,
                        )
                    }
                }
            }
        }

        // Floats above the list rather than sitting inside it - see
        // HomeScanFab's own doc for why. This sits outside the Scaffold, so it
        // gets neither the Scaffold's system-bar inset nor the list's padding:
        // both are applied here, the inset first, exactly as the nav bar does.
        HomeScanFab(
            onClick = { onOpenTool(ToolDestination.SCAN) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = Dewey.spacing.gutter, bottom = NavBarClearance),
        )
    }
}

@Preview(heightDp = 900, widthDp = 390)
@Composable
private fun HomeLightPreview() {
    DeweyTheme(dark = false) {
        HomeContent(
            state = previewState,
            onOpenTool = {},
            onPlanPillClick = {},
            onAssistantClick = {},
            onOpenFile = {},
            onOpenBills = {},
            onOpenCategory = {},
        )
    }
}

@Preview(heightDp = 900, widthDp = 390)
@Composable
private fun HomeDarkPreview() {
    DeweyTheme(dark = true) {
        HomeContent(
            state = previewState,
            onOpenTool = {},
            onPlanPillClick = {},
            onAssistantClick = {},
            onOpenFile = {},
            onOpenBills = {},
            onOpenCategory = {},
        )
    }
}

@Preview(heightDp = 900, widthDp = 390)
@Composable
private fun HomeFreeEmptyPreview() {
    DeweyTheme(dark = false) {
        HomeContent(
            state = HomeUiState(isEntitled = false),
            onOpenTool = {},
            onPlanPillClick = {},
            onAssistantClick = {},
            onOpenFile = {},
            onOpenBills = {},
            onOpenCategory = {},
        )
    }
}

private val previewNow = System.currentTimeMillis()
private val previewToday: LocalDate = Instant.ofEpochMilli(previewNow).atZone(ZoneId.systemDefault()).toLocalDate()

private val previewState = HomeUiState(
    isEntitled = true,
    recent = listOf(
        RecentFile("content://1", "Scan_20260910_001.pdf", RecentKind.SCAN, previewNow - 5 * 60_000L),
        RecentFile("content://2", "Lydec janvier 2026.pdf", RecentKind.TOOL, previewNow - 90 * 60_000L),
        RecentFile("content://3", "Merged (3 pages).pdf", RecentKind.TOOL, previewNow - 26 * 3_600_000L),
    ),
    billsGlance = BillsGlance(
        dueSoonCount = 3,
        soonest = Document(
            id = 1, uri = "u1", displayName = "Lydec.pdf", sizeBytes = 0, lastModified = 0,
            docType = DocType.UTILITY_BILL, vendor = "Lydec", amount = 281.26, currency = "MAD",
            dueDate = previewToday.plusDays(5),
        ),
    ),
    topCategories = listOf(
        CategoryCount("Bills", 12),
        CategoryCount("Voiture", 7),
        CategoryCount("Bank", 5),
        CategoryCount("Insurance", 3),
    ),
    now = previewNow,
)
