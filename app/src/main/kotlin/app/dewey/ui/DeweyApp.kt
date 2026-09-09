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
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.dewey.di.AppContainer
import app.dewey.domain.model.Document
import app.dewey.ui.bills.BillsScreen
import app.dewey.ui.bills.BillsViewModel
import app.dewey.ui.library.LibraryScreen
import app.dewey.ui.library.LibraryViewModel
import app.dewey.ui.search.SearchScreen
import app.dewey.ui.components.BottomNav
import app.dewey.ui.components.NavDestination
import app.dewey.ui.search.SearchViewModel
import app.dewey.ui.theme.Dewey

private object Routes {
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val BILLS = "bills"
}

/**
 * The three destinations, in the order they appear in the bar.
 *
 * Three, not the four the design mock showed. Notes and the PDF toolkit are not
 * built — the toolkit was cut deliberately — and a tab that opens an empty
 * screen costs more trust than the tab was ever going to earn.
 */
private val Destinations = listOf(
    NavDestination(Routes.LIBRARY, "Library", Icons.Outlined.Home),
    NavDestination(Routes.BILLS, "Bills", Icons.Outlined.Receipt),
    NavDestination(Routes.SEARCH, "Find", Icons.Outlined.Search),
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
    val onOpenDocument: (Document) -> Unit = { document ->
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(document.uri), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app installed to open a PDF", Toast.LENGTH_SHORT).show()
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: Routes.LIBRARY

    Box(Modifier.fillMaxSize().background(Dewey.colors.paper)) {
        NavHost(navController = navController, startDestination = Routes.LIBRARY) {
            composable(Routes.LIBRARY) {
                val model: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
                LibraryScreen(
                    viewModel = model,
                    entitlements = container.entitlements,
                    onOpenDocument = onOpenDocument,
                )
            }
            composable(Routes.SEARCH) {
                val model: SearchViewModel = viewModel(factory = SearchViewModel.factory(container))
                SearchScreen(
                    viewModel = model,
                    onOpenDocument = onOpenDocument,
                )
            }
            composable(Routes.BILLS) {
                val model: BillsViewModel = viewModel(factory = BillsViewModel.factory(container))
                BillsScreen(
                    viewModel = model,
                    onOpenDocument = onOpenDocument,
                )
            }
        }

        BottomNav(
            destinations = Destinations,
            current = current,
            onSelect = { route ->
                if (route != current) {
                    navController.navigate(route) {
                        // Tabs, not a stack. Without this, moving between them
                        // piles up back entries and the system back gesture
                        // walks a history nobody built on purpose. The start
                        // destination is kept so back always leaves the app
                        // from the library rather than from wherever you were.
                        popUpTo(Routes.LIBRARY) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
