package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.data.local.CompanyDao
import com.vague.crewtally.data.local.ProjectDao
import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.data.local.ProjectStatus
import com.vague.crewtally.data.local.RosterEntryDao
import com.vague.crewtally.data.local.RosterEntryEntity
import com.vague.crewtally.data.local.RosterRowSummary
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the view-first project detail screen: the project itself, its company's name, and
 * its active roster (soft-removed rows already excluded by the DAO query).
 *
 * Mutating actions ([markCompleted], [reopen], [removeFromRoster]) take the current entity
 * as a parameter rather than reading it back off a [StateFlow] — the caller (the screen) is
 * always holding the exact row it just rendered and confirmed an action against, so acting
 * on that avoids a stale-read race and keeps this class trivial to unit test with fakes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDetailViewModel(
    projectId: String,
    private val projectDao: ProjectDao,
    companyDao: CompanyDao,
    private val rosterEntryDao: RosterEntryDao,
) : ViewModel() {

    val project: StateFlow<ProjectEntity?> = projectDao.observeById(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val companyName: StateFlow<String> = project
        .flatMapLatest { current ->
            val companyId = current?.companyId
            if (companyId == null) flowOf(null) else companyDao.observeById(companyId)
        }
        .map { it?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), "")

    val roster: StateFlow<List<RosterRowSummary>> = rosterEntryDao.observeActiveRosterForProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun markCompleted(current: ProjectEntity) {
        viewModelScope.launch {
            projectDao.upsert(current.copy(status = ProjectStatus.COMPLETED, endDate = LocalDate.now()))
        }
    }

    fun reopen(current: ProjectEntity) {
        viewModelScope.launch {
            projectDao.upsert(current.copy(status = ProjectStatus.ACTIVE, endDate = null))
        }
    }

    /** Soft-removes a roster row: [RosterEntryEntity.removedAt] is stamped, never deleted. */
    fun removeFromRoster(entry: RosterEntryEntity) {
        viewModelScope.launch {
            rosterEntryDao.upsert(entry.copy(removedAt = System.currentTimeMillis()))
        }
    }

    companion object {
        fun factory(
            projectId: String,
            projectDao: ProjectDao,
            companyDao: CompanyDao,
            rosterEntryDao: RosterEntryDao,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProjectDetailViewModel(projectId, projectDao, companyDao, rosterEntryDao) }
        }
    }
}
