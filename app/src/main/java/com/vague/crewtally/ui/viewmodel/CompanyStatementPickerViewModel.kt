package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.data.local.CompanyDao
import com.vague.crewtally.data.local.CompanyEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * [isLoading] defaults true and only clears on Room's first emission (matches
 * [com.vague.crewtally.ui.clerk.ClerkListViewModel]'s convention) so the picker screen can show
 * a loading indicator instead of flashing "No companies yet." before that first emission lands.
 */
data class CompanyStatementPickerUiState(
    val companies: List<CompanyEntity> = emptyList(),
    val isLoading: Boolean = true,
)

/** Backs the v1.1 company statement picker screen: non-archived companies only (LOCKED). */
class CompanyStatementPickerViewModel(companyDao: CompanyDao) : ViewModel() {

    val uiState: StateFlow<CompanyStatementPickerUiState> = companyDao
        .observeActive()
        .map { CompanyStatementPickerUiState(companies = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CompanyStatementPickerUiState())

    companion object {
        fun factory(companyDao: CompanyDao): ViewModelProvider.Factory = viewModelFactory {
            initializer { CompanyStatementPickerViewModel(companyDao) }
        }
    }
}
