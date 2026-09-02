package app.dewey.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.dewey.di.AppContainer
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

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            val model: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
            LibraryScreen(
                viewModel = model,
                onSearch = { navController.navigate(Routes.SEARCH) },
                onOpenDocument = { },
            )
        }
        composable(Routes.SEARCH) {
            val model: SearchViewModel = viewModel(factory = SearchViewModel.factory(container))
            SearchScreen(
                viewModel = model,
                onOpenDocument = { },
            )
        }
    }
}
