package app.dewey.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.dewey.di.AppContainer
import app.dewey.domain.model.Document
import app.dewey.ui.library.LibraryScreen
import app.dewey.ui.library.LibraryViewModel
import app.dewey.ui.search.SearchScreen
import app.dewey.ui.search.SearchViewModel

private object Routes {
    const val LIBRARY = "library"
    const val SEARCH = "search"
}

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

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            val model: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
            LibraryScreen(
                viewModel = model,
                onSearch = { navController.navigate(Routes.SEARCH) },
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
    }
}
