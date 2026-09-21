package com.vague.crewtally.ui.clerk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.data.local.ClerkDao
import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.ui.util.matchesSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One row on the clerk list: entity fields flattened to what the row/form actually need. */
data class ClerkRow(
    val id: String,
    val name: String,
    val phone: String,
    val notes: String,
    val isArchived: Boolean,
)

data class ClerkListUiState(
    val activeClerks: List<ClerkRow> = emptyList(),
    val archivedClerks: List<ClerkRow> = emptyList(),
    val searchQuery: String = "",
    val showArchived: Boolean = false,
    /** True once every clerk ever added is archived-out or there simply are none — used to
     *  tell "nothing stored yet" (show the CTA empty state) apart from "search matched
     *  nothing" (a smaller inline hint). */
    val hasAnyClerks: Boolean = false,
    val isLoading: Boolean = true,
    /** Total active/archived clerk counts for the "12 clerks" / "3 archived" summary under the
     *  screen title (v1.2 LOCKED). Deliberately NOT filtered by [searchQuery] — this is a
     *  standing "how many clerks do you have" fact, not a "how many matched" count, so it
     *  doesn't flicker while the user types in the search box. */
    val activeCount: Int = 0,
    val archivedCount: Int = 0,
)

sealed interface ClerkListEvent {
    data class SearchQueryChanged(val query: String) : ClerkListEvent
    data class ShowArchivedChanged(val show: Boolean) : ClerkListEvent
}

/**
 * Drives the Clerks tab: the alphabetical active-clerk list (DB already sorts by name),
 * filtered live by [ClerkListEvent.SearchQueryChanged] against name and phone, plus an
 * opt-in archived section behind [ClerkListEvent.ShowArchivedChanged].
 */
class ClerkListViewModel(private val clerkDao: ClerkDao) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val showArchived = MutableStateFlow(false)

    val uiState: StateFlow<ClerkListUiState> = combine(
        clerkDao.observeAll(),
        searchQuery,
        showArchived,
    ) { clerks, query, isShowingArchived ->
        val rows = clerks.map { it.toRow() }
        val matching = rows.filter { matchesSearch(query, it.name, it.phone) }
        ClerkListUiState(
            activeClerks = matching.filter { !it.isArchived },
            archivedClerks = if (isShowingArchived) matching.filter { it.isArchived } else emptyList(),
            searchQuery = query,
            showArchived = isShowingArchived,
            hasAnyClerks = clerks.isNotEmpty(),
            isLoading = false,
            activeCount = rows.count { !it.isArchived },
            archivedCount = rows.count { it.isArchived },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClerkListUiState())

    fun onEvent(event: ClerkListEvent) {
        when (event) {
            is ClerkListEvent.SearchQueryChanged -> searchQuery.value = event.query
            is ClerkListEvent.ShowArchivedChanged -> showArchived.value = event.show
        }
    }

    companion object {
        fun factory(clerkDao: ClerkDao): ViewModelProvider.Factory = viewModelFactory {
            initializer { ClerkListViewModel(clerkDao) }
        }
    }
}

private fun ClerkEntity.toRow() = ClerkRow(
    id = id,
    name = name,
    phone = phone,
    notes = notes,
    isArchived = !active,
)
