package app.dewey.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.BuildConfig
import app.dewey.billing.Entitlements
import app.dewey.data.repository.DocumentRepository
import app.dewey.data.storage.DocumentTreeStore
import app.dewey.di.AppContainer
import app.dewey.ui.billing.LibrarianPaywall
import app.dewey.ui.components.GlassCard
import app.dewey.ui.components.IconTile
import app.dewey.ui.components.NavBarClearance
import app.dewey.ui.components.PrimaryAction
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.theme.Hue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
)

class MeViewModel(
    repository: DocumentRepository,
    treeStore: DocumentTreeStore,
    private val entitlements: Entitlements,
) : ViewModel() {

    val state: StateFlow<MeUiState> = combine(
        entitlements.isEntitled,
        repository.observeCount(),
        repository.observeBills(),
        treeStore.grantedTrees,
    ) { entitled, count, bills, trees ->
        MeUiState(entitled, entitlements.isConfigured, documents = count, bills = bills.size, folders = trees.size)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MeUiState(isEntitled = !entitlements.isConfigured, hasPurchases = entitlements.isConfigured),
    )

    fun onPaywallDismissed() {
        viewModelScope.launch { entitlements.refresh() }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MeViewModel(container.documentRepository, container.documentTreeStore, container.entitlements) as T
        }
    }
}

/**
 * The Me tab: your plan, what Dewey is holding for you, and how the app looks.
 *
 * Deliberately small for now. Settings grow here as they come to exist, rather
 * than as rows that lead nowhere.
 */
@Composable
fun MeScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val model: MeViewModel = viewModel(factory = MeViewModel.factory(container))
    val state by model.state.collectAsStateWithLifecycle()
    var showPaywall by remember { mutableStateOf(false) }

    if (showPaywall) {
        LibrarianPaywall(
            entitlements = container.entitlements,
            onDismiss = {
                showPaywall = false
                model.onPaywallDismissed()
            },
        )
    }

    MeContent(state = state, onUpgrade = { showPaywall = true }, modifier = modifier)
}

@Composable
private fun MeContent(state: MeUiState, onUpgrade: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = Dewey.spacing.gutter, end = Dewey.spacing.gutter, top = Dewey.spacing.block, bottom = NavBarClearance),
    ) {
        Text("Me", style = Dewey.type.Display, color = Dewey.colors.ink)
        Spacer(Modifier.height(Dewey.spacing.gutter))

        PlanCard(state = state, onUpgrade = onUpgrade)
        Spacer(Modifier.height(Dewey.spacing.row))

        Row(horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row)) {
            Stat(Icons.Rounded.Description, Dewey.colors.hues.pages, state.documents, if (state.documents == 1) "Document" else "Documents")
            Stat(Icons.Rounded.Receipt, Dewey.colors.hues.bills, state.bills, if (state.bills == 1) "Bill" else "Bills")
            Stat(Icons.Rounded.Folder, Dewey.colors.hues.convert, state.folders, if (state.folders == 1) "Folder" else "Folders")
        }
        Spacer(Modifier.height(Dewey.spacing.row))

        InfoRow(Icons.Rounded.Palette, Dewey.colors.hues.mark, "Appearance", "Light and dark follow your phone's setting.")
        Spacer(Modifier.height(Dewey.spacing.row))
        InfoRow(Icons.Rounded.Info, Dewey.colors.hues.scan, "Dewey ${BuildConfig.VERSION_NAME}", "Open source under the MIT licence. Your documents stay on this phone.")
    }
}

@Composable
private fun PlanCard(state: MeUiState, onUpgrade: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), padding = Dewey.spacing.gutter) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Star, Dewey.colors.hues.assistant, size = 52.dp)
            Spacer(Modifier.width(Dewey.spacing.row))
            Column {
                Text(if (state.isEntitled) "Librarian" else "Free", style = Dewey.type.Title, color = Dewey.colors.ink)
                Text(
                    text = when {
                        !state.hasPurchases -> "This build has no purchase system, so everything is unlocked."
                        state.isEntitled -> "Sorting, your documents, bills, notes and the assistant are all yours."
                        else -> "The scanner and PDF tools are free. Librarian adds your documents, bills, notes and the assistant."
                    },
                    style = Dewey.type.Meta,
                    color = Dewey.colors.inkMuted,
                )
            }
        }
        if (!state.isEntitled) {
            Spacer(Modifier.height(Dewey.spacing.gutter))
            PrimaryAction(label = "Unlock Librarian", onClick = onUpgrade)
        }
    }
}

@Composable
private fun RowScope.Stat(icon: ImageVector, hue: Hue, count: Int, label: String) {
    GlassCard(modifier = Modifier.weight(1f)) {
        IconTile(icon, hue, size = 36.dp)
        Spacer(Modifier.height(Dewey.spacing.row))
        Text(count.toString(), style = Dewey.type.Title, color = Dewey.colors.ink)
        Text(label, style = Dewey.type.Meta, color = Dewey.colors.inkMuted)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, hue: Hue, title: String, detail: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, hue, size = 40.dp)
            Spacer(Modifier.width(Dewey.spacing.row))
            Column {
                Text(title, style = Dewey.type.Title, color = Dewey.colors.ink)
                Text(detail, style = Dewey.type.Meta, color = Dewey.colors.inkMuted)
            }
        }
    }
}

@Preview(heightDp = 800, widthDp = 390)
@Composable
private fun MeLightPreview() {
    DeweyTheme(dark = false) {
        MeContent(MeUiState(isEntitled = false, hasPurchases = true, documents = 114, bills = 9, folders = 1), onUpgrade = {})
    }
}

@Preview(heightDp = 800, widthDp = 390)
@Composable
private fun MeDarkPreview() {
    DeweyTheme(dark = true) {
        MeContent(MeUiState(isEntitled = true, hasPurchases = true, documents = 114, bills = 9, folders = 1), onUpgrade = {})
    }
}
