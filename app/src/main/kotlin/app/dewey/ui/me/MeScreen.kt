package app.dewey.ui.me

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.BuildConfig
import app.dewey.assistant.AssistantQuota
import app.dewey.data.settings.ThemeMode
import app.dewey.di.AppContainer
import app.dewey.ui.billing.LibrarianPaywall
import app.dewey.ui.components.GlassCard
import app.dewey.ui.components.IconTile
import app.dewey.ui.components.NavBarClearance
import app.dewey.ui.documents.folderDisplayName
import app.dewey.ui.library.LibraryViewModel
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.theme.Hue

/** Where the repo lives — see the About card's "View source on GitHub". */
private const val GITHUB_URL = "https://github.com/Yunsmn/dewey"

/**
 * The Me tab: your plan, how the app looks, what's linked, and the honest
 * account of what leaves the phone.
 *
 * [MeContent] is the stateless layout every preview renders directly. This
 * function only wires it to [MeViewModel], the paywall, and the same
 * folder-picker-and-grant path the Documents tab uses — see
 * [app.dewey.ui.library.LibraryViewModel.onFolderGranted].
 */
@Composable
fun MeScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val model: MeViewModel = viewModel(factory = MeViewModel.factory(container))
    val state by model.state.collectAsStateWithLifecycle()
    var showPaywall by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showPaywall) {
        LibrarianPaywall(
            entitlements = container.entitlements,
            onDismiss = {
                showPaywall = false
                model.onPaywallDismissed()
            },
        )
    }

    // A one-shot message rather than part of state: re-showing "Purchases
    // restored" on every rotation would be news the second time it isn't.
    LaunchedEffect(model) {
        model.restoreEvents.collect { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    // Scoped to this screen's own back-stack entry, not shared with the
    // Documents tab's instance — both read and write through the same
    // AppContainer singletons (the tree store, the task runner), so granting
    // a folder here starts the identical indexing job.
    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
    val grantedTrees by container.documentTreeStore.grantedTrees.collectAsStateWithLifecycle(initialValue = emptyList())
    val folderName = grantedTrees.firstOrNull()?.let { uri -> folderDisplayName(DocumentsContract.getTreeDocumentId(uri)) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(libraryViewModel::onFolderGranted)
    }

    MeContent(
        state = state,
        folderName = folderName,
        onUpgrade = { showPaywall = true },
        onRestore = model::onRestorePurchases,
        onManageSubscription = { url -> openUrl(context, url.toString()) },
        onThemeModeSelected = model::onThemeModeSelected,
        onChangeFolder = { pickFolder.launch(null) },
        onViewSource = { openUrl(context, GITHUB_URL) },
        modifier = modifier,
    )
}

/** Opens [url] in a browser, or says there isn't one — the emulator this runs on for a demo may have none, same as [app.dewey.ui.DeweyApp]'s PDF viewer check. */
private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No browser available to open this link", Toast.LENGTH_SHORT).show()
    }
}

/**
 * The layout every preview below renders directly, with no [AppContainer]
 * and no ViewModel in sight.
 */
@Composable
private fun MeContent(
    state: MeUiState,
    folderName: String?,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
    onManageSubscription: (Uri) -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onChangeFolder: () -> Unit,
    onViewSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = Dewey.spacing.gutter, end = Dewey.spacing.gutter, top = Dewey.spacing.block, bottom = NavBarClearance),
    ) {
        Text("Me", style = Dewey.type.Display, color = Dewey.colors.ink)
        Spacer(Modifier.height(Dewey.spacing.gutter))

        MePlanSection(
            isEntitled = state.isEntitled,
            hasPurchases = state.hasPurchases,
            isRestoring = state.isRestoring,
            managementUrl = state.managementUrl,
            onUpgrade = onUpgrade,
            onRestore = onRestore,
            onManageSubscription = onManageSubscription,
        )
        Spacer(Modifier.height(Dewey.spacing.row))

        Row(horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row)) {
            Stat(Icons.Rounded.Description, Dewey.colors.hues.pages, state.documents, if (state.documents == 1) "Document" else "Documents")
            Stat(Icons.Rounded.Receipt, Dewey.colors.hues.bills, state.bills, if (state.bills == 1) "Bill" else "Bills")
            Stat(Icons.Rounded.Folder, Dewey.colors.hues.convert, state.folders, if (state.folders == 1) "Folder" else "Folders")
        }
        Spacer(Modifier.height(Dewey.spacing.row))

        MeAppearanceSection(themeMode = state.themeMode, onSelect = onThemeModeSelected)
        Spacer(Modifier.height(Dewey.spacing.row))

        if (state.isEntitled) {
            MeLibrarySection(folderName = folderName, documentCount = state.documents, onChangeFolder = onChangeFolder)
            Spacer(Modifier.height(Dewey.spacing.row))
            MeAssistantSection(questionsLeft = state.assistantQuestionsLeft, dailyLimit = AssistantQuota.DAILY_LIMIT)
            Spacer(Modifier.height(Dewey.spacing.row))
        }

        MePrivacySection()
        Spacer(Modifier.height(Dewey.spacing.row))
        MeAboutSection(versionName = BuildConfig.VERSION_NAME, onViewSource = onViewSource)
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

@Preview(heightDp = 900, widthDp = 390)
@Composable
private fun MeLightPreview() {
    DeweyTheme(dark = false) {
        MeContent(
            state = MeUiState(
                isEntitled = false,
                hasPurchases = true,
                documents = 114,
                bills = 9,
                folders = 1,
                themeMode = ThemeMode.SYSTEM,
                assistantQuestionsLeft = 50,
            ),
            folderName = "Downloads",
            onUpgrade = {},
            onRestore = {},
            onManageSubscription = {},
            onThemeModeSelected = {},
            onChangeFolder = {},
            onViewSource = {},
        )
    }
}

@Preview(heightDp = 900, widthDp = 390)
@Composable
private fun MeDarkPreview() {
    DeweyTheme(dark = true) {
        MeContent(
            state = MeUiState(
                isEntitled = true,
                hasPurchases = true,
                documents = 114,
                bills = 9,
                folders = 1,
                themeMode = ThemeMode.DARK,
                assistantQuestionsLeft = 37,
                managementUrl = Uri.parse("https://apps.apple.com/account/subscriptions"),
            ),
            folderName = "Downloads",
            onUpgrade = {},
            onRestore = {},
            onManageSubscription = {},
            onThemeModeSelected = {},
            onChangeFolder = {},
            onViewSource = {},
        )
    }
}
