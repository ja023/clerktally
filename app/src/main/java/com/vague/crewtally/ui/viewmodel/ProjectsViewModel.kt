package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.data.local.ProjectDao
import com.vague.crewtally.data.local.ProjectSummary
import com.vague.crewtally.data.local.ProjectStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Which half of the Projects tab's segmented control is showing. */
enum class ProjectSegment { ACTIVE, COMPLETED }

/**
 * Backs the Projects tab list screen. Active and completed projects are two independent
 * flows straight from [ProjectDao] — the segmented control (owned by the screen as simple
 * UI state) just chooses which one to render, so switching segments never re-queries.
 */
class ProjectsViewModel(private val projectDao: ProjectDao) : ViewModel() {

    val activeProjects: StateFlow<List<ProjectSummary>> = projectDao
        .observeSummariesByStatus(ProjectStatus.ACTIVE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val completedProjects: StateFlow<List<ProjectSummary>> = projectDao
        .observeSummariesByStatus(ProjectStatus.COMPLETED)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    companion object {
        fun factory(projectDao: ProjectDao): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProjectsViewModel(projectDao) }
        }
    }
}
