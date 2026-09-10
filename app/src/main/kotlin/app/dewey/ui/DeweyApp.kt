package app.dewey.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.dewey.di.AppContainer
import app.dewey.domain.model.Document
import app.dewey.ui.billing.LibrarianGate
import app.dewey.ui.bills.BillsScreen
import app.dewey.ui.bills.BillsViewModel
import app.dewey.ui.components.BottomNav
import app.dewey.ui.components.NavDestination
import app.dewey.ui.documents.DocumentsScreen
import app.dewey.ui.home.HomeScreen
import app.dewey.ui.me.MeScreen
import app.dewey.ui.theme.Dewey
import app.dewey.ui.tools.ToolDestination
import app.dewey.ui.tools.ToolScreen

internal object Routes {
    const val HOME = "home"
    const val DOCUMENTS = "documents"
    const val NOTES = "notes"
    const val ME = "me"

    const val CATEGORY_ARG = "category"
    const val DOCUMENTS_PATTERN = "$DOCUMENTS?$CATEGORY_ARG={$CATEGORY_ARG}"

    /** Documents, optionally opened on one category — how Home's category chips land there. */
    fun documents(category: String?): String =
        if (category == null) DOCUMENTS else "$DOCUMENTS?$CATEGORY_ARG=${Uri.encode(category)}"
}

/**
 * The tab a route lives under.
 *
 * Tool screens are opened from Home's grid, so Home stays lit while one is
 * open; Documents carries an optional query, which is not part of its identity
 * as a tab.
 */
internal fun tabForRoute(route: String?): String = when {
    route == null -> Routes.HOME
    route.startsWith("tools/") -> Routes.HOME
    else -> route.substringBefore('?')
}

/**
 * The four destinations, in the order they appear in the bar.
 *
 * Home is the free half of the app — the scanner and the PDF tools. The other
 * three are Librarian's, and each one explains itself when locked rather than
 * being hidden: see [LibrarianGate].
 */
private val Destinations = listOf(
    NavDestination(Routes.HOME, "Home", Icons.Rounded.Home),
    NavDestination(Routes.DOCUMENTS, "Documents", Icons.Rounded.Folder),
    NavDestination(Routes.NOTES, "Notes", Icons.Rounded.EditNote),
    NavDestination(Routes.ME, "Me", Icons.Rounded.Person),
)

@Composable
fun DeweyApp(container: AppContainer) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // A SAF content URI, not a path, and the granting app (this one) must hand
    // the viewer explicit read permission on it — a viewer with no prior grant
    // on that URI otherwise just fails to open it. There may be no PDF viewer
    // at all — the emulator this runs on for the demo likely has none — so
    // that failure is caught and told to the user instead of crashing the app.
    val openUri: (String) -> Unit = { uri ->
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uri), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app installed to open a PDF", Toast.LENGTH_SHORT).show()
        }
    }
    val onOpenDocument: (Document) -> Unit = { document -> openUri(document.uri) }

    val backStack by navController.currentBackStackEntryAsState()
    val currentTab = tabForRoute(backStack?.destination?.route)

    // Tabs, not a stack. Without popUpTo, moving between them piles up back
    // entries and the system back gesture walks a history nobody built on
    // purpose. Home is kept so back always leaves the app from there.
    // [restore] is false when the route carries a new argument — a category
    // from Home — which restoring the tab's saved state would silently drop.
    fun openTab(route: String, restore: Boolean = true) {
        navController.navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = restore
        }
    }

    Box(Modifier.fillMaxSize().background(Dewey.colors.paper)) {
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    container = container,
                    onOpenTool = { tool -> navController.navigate(tool.route) },
                    // The assistant lives in the Documents ask bar until it has a screen of its own.
                    onOpenAssistant = { openTab(Routes.DOCUMENTS) },
                    onOpenMe = { openTab(Routes.ME) },
                    onOpenFile = openUri,
                    onOpenBills = { openTab(Routes.NOTES) },
                    onOpenCategory = { label -> openTab(Routes.documents(label), restore = false) },
                )
            }
            composable(
                route = Routes.DOCUMENTS_PATTERN,
                arguments = listOf(
                    navArgument(Routes.CATEGORY_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                DocumentsScreen(
                    container = container,
                    onOpenDocument = onOpenDocument,
                    initialCategory = entry.arguments?.getString(Routes.CATEGORY_ARG),
                )
            }
            composable(Routes.NOTES) {
                LibrarianGate(
                    entitlements = container.entitlements,
                    icon = Icons.Rounded.EditNote,
                    hue = Dewey.colors.hues.bills,
                    title = "Notes and bills",
                    blurb = "Every bill Dewey finds in your documents, with what is due and when — and notes of your own beside them.",
                ) {
                    val model: BillsViewModel = viewModel(factory = BillsViewModel.factory(container))
                    BillsScreen(viewModel = model, onOpenDocument = onOpenDocument)
                }
            }
            composable(Routes.ME) {
                MeScreen(container = container)
            }
            // One destination per tool, generated from the same enum Home's
            // grid lists, so the two cannot disagree about what exists.
            for (tool in ToolDestination.entries) {
                composable(tool.route) {
                    ToolScreen(tool = tool, toolkit = container.pdfToolkit)
                }
            }
        }

        BottomNav(
            destinations = Destinations,
            current = currentTab,
            onSelect = { route -> if (route != currentTab) openTab(route) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
