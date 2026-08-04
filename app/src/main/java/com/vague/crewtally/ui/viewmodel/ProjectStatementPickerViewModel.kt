package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.data.local.ProjectDao
import com.vague.crewtally.data.local.ProjectSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * [isLoading] defaults true and only clears on Room's first emission (matches
 * [com.vague.crewtally.ui.clerk.ClerkListViewModel]'s convention) so the picker screen can show
 * a loading indicator instead of flashing "No projects yet." before that first emission lands.
 */
data class ProjectStatementPickerUiState(
    val projects: List<ProjectSummary> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Backs the v1.1 project statement picker screen: every project regardless of status (LOCKED:
 * "include completed projects too" — unlike the Projects tab, which is always scoped to one
 * Active/Completed segment).
 */
class ProjectStatementPickerViewModel(projectDao: ProjectDao) : ViewModel() {

    val uiState: StateFlow<ProjectStatementPickerUiState> = projectDao
        .observeAllSummaries()
        .map { ProjectStatementPickerUiState(projects = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ProjectStatementPickerUiState())

    companion object {
        fun factory(projectDao: ProjectDao): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProjectStatementPickerViewModel(projectDao) }
        }
    }
}
