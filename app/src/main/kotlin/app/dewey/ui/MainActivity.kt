package app.dewey.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.DeweyApplication
import app.dewey.ui.library.LibraryScreen
import app.dewey.ui.library.LibraryViewModel
import app.dewey.ui.theme.DeweyTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as DeweyApplication).container

        setContent {
            DeweyTheme {
                val model: LibraryViewModel = viewModel(
                    factory = LibraryViewModel.factory(container)
                )
                LibraryScreen(
                    viewModel = model,
                    // Opening a document is stage 6's viewer. Until then the row
                    // is still tappable, so the interaction is not a dead end
                    // that has to be retrofitted later.
                    onOpenDocument = {},
                )
            }
        }
    }
}
