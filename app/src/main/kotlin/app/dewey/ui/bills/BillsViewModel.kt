package app.dewey.ui.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.data.repository.DocumentRepository
import app.dewey.di.AppContainer
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BillsUiState(
    val sections: List<BillSection> = emptyList(),
    /**
     * The date every row's due-date wording is computed against - carried in
     * state rather than read fresh with `LocalDate.now()` inside the screen,
     * so a row's "due in 5 days" always agrees with which section it was
     * sorted into.
     */
    val today: LocalDate = LocalDate.now(),
) {
    val isEmpty: Boolean get() = sections.isEmpty()
}

/**
 * State for the bills screen.
 *
 * [today] is a constructor parameter, not a call to `LocalDate.now()` inside
 * the flow's own map - the same reasoning as [groupBills]. A test can hand
 * this a fixed date; production gets the real clock.
 */
class BillsViewModel(
    private val repository: DocumentRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    val state: StateFlow<BillsUiState> = repository.observeBills()
        .map { documents ->
            // Read once per emission, not once per row - see BillsUiState.today.
            val asOf = today()
            BillsUiState(sections = groupBills(documents, asOf), today = asOf)
        }
        .stateIn(
            scope = viewModelScope,
            // Same rationale as LibraryViewModel: keeps the list alive across a
            // rotation instead of reloading from the database every time.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BillsUiState(),
        )

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BillsViewModel(repository = container.documentRepository) as T
        }
    }
}
