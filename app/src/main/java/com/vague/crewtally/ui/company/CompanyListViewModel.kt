package com.vague.crewtally.ui.company

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.data.local.CompanyDao
import com.vague.crewtally.data.local.CompanyEntity
import com.vague.crewtally.ui.util.matchesSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One row on the company list: entity fields flattened to what the row/form actually need. */
data class CompanyRow(
    val id: String,
    val name: String,
    val contactPerson: String,
    val phone: String,
    val notes: String,
    val isArchived: Boolean,
)

data class CompanyListUiState(
    val activeCompanies: List<CompanyRow> = emptyList(),
    val archivedCompanies: List<CompanyRow> = emptyList(),
    val showArchived: Boolean = false,
    /** True once a company has ever been added — companies are few, so unlike Clerks this
     *  list has no search box (Phase 1 scope), just the CTA-empty-state/has-content split. */
    val hasAnyCompanies: Boolean = false,
    val isLoading: Boolean = true,
)

sealed interface CompanyListEvent {
    data class ShowArchivedChanged(val show: Boolean) : CompanyListEvent
}

/**
 * Drives the Companies list under More. No search box (Phase 1 LOCKED: "companies are
 * few"), but the same active/archived split and archive toggle as Clerks, so the pattern
 * reads identically across both list screens.
 */
class CompanyListViewModel(private val companyDao: CompanyDao) : ViewModel() {

    private val showArchived = MutableStateFlow(false)

    val uiState: StateFlow<CompanyListUiState> = combine(
        companyDao.observeAll(),
        showArchived,
    ) { companies, isShowingArchived ->
        val rows = companies.map { it.toRow() }
        CompanyListUiState(
            activeCompanies = rows.filter { !it.isArchived },
            archivedCompanies = if (isShowingArchived) rows.filter { it.isArchived } else emptyList(),
            showArchived = isShowingArchived,
            hasAnyCompanies = companies.isNotEmpty(),
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompanyListUiState())

    fun onEvent(event: CompanyListEvent) {
        when (event) {
            is CompanyListEvent.ShowArchivedChanged -> showArchived.value = event.show
        }
    }

    companion object {
        fun factory(companyDao: CompanyDao): ViewModelProvider.Factory = viewModelFactory {
            initializer { CompanyListViewModel(companyDao) }
        }
    }
}

private fun CompanyEntity.toRow() = CompanyRow(
    id = id,
    name = name,
    contactPerson = contactPerson,
    phone = phone,
    notes = notes,
    isArchived = archived,
)
