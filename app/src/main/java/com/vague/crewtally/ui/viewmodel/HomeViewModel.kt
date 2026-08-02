package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.balance.BalanceCalculator
import com.vague.crewtally.balance.OutstandingTotal
import com.vague.crewtally.data.local.AttendanceEntryDao
import com.vague.crewtally.data.local.ClerkDao
import com.vague.crewtally.data.local.ExtraPayLineDao
import com.vague.crewtally.data.local.PaymentDao
import com.vague.crewtally.data.local.ProjectDao
import com.vague.crewtally.data.local.ProjectStatus
import com.vague.crewtally.data.local.ProjectSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One owed-clerk row on the Home dashboard: who is owed, on which project, how much, which currency. */
data class OwedClerkRow(
    val projectId: String,
    val clerkId: String,
    val clerkName: String,
    val projectName: String,
    val owed: Long,
    val currency: String,
)

data class HomeUiState(
    /** Active projects, each with a "Take attendance" shortcut (name + company). */
    val activeProjects: List<ProjectSummary> = emptyList(),
    /** Outstanding total per currency (positive balances only, never summed across currencies). */
    val outstanding: List<OutstandingTotal> = emptyList(),
    /** Clerks who are owed money, sorted by amount owed descending. */
    val owedClerks: List<OwedClerkRow> = emptyList(),
    val isLoaded: Boolean = false,
)

/**
 * Backs the Home tab — the projects-first dashboard (LOCKED Phase 4). Active projects come
 * straight from the project summaries; the outstanding totals and owed-clerks list are derived
 * by folding the three money roll-ups into per-(project, clerk) balances (nothing stored) and
 * joining them with project and clerk names. Balances are drawn across ALL projects, not just
 * active ones — a completed project can still have open books. Cross-currency amounts are never
 * summed (LOCKED); each currency stands alone.
 */
class HomeViewModel(
    projectDao: ProjectDao,
    clerkDao: ClerkDao,
    attendanceEntryDao: AttendanceEntryDao,
    extraPayLineDao: ExtraPayLineDao,
    paymentDao: PaymentDao,
) : ViewModel() {

    private val balances = combine(
        attendanceEntryDao.observeEarningsRollup(),
        extraPayLineDao.observeExtrasRollup(),
        paymentDao.observePaymentsRollup(),
    ) { earnings, extras, payments -> BalanceCalculator.rollup(earnings, extras, payments) }

    val uiState: StateFlow<HomeUiState> = combine(
        projectDao.observeSummariesByStatus(ProjectStatus.ACTIVE),
        projectDao.observeAll(),
        clerkDao.observeAll(),
        balances,
    ) { activeProjects, allProjects, clerks, balanceList ->
        val currencyByProject = allProjects.associate { it.id to it.currency }
        val projectNameById = allProjects.associate { it.id to it.name }
        val clerkNameById = clerks.associate { it.id to it.name }

        val owedClerks = balanceList
            .filter { it.owed > 0L }
            .map { balance ->
                OwedClerkRow(
                    projectId = balance.projectId,
                    clerkId = balance.clerkId,
                    clerkName = clerkNameById[balance.clerkId].orEmpty(),
                    projectName = projectNameById[balance.projectId].orEmpty(),
                    owed = balance.owed,
                    currency = currencyByProject[balance.projectId].orEmpty(),
                )
            }
            .sortedByDescending { it.owed }

        HomeUiState(
            activeProjects = activeProjects,
            outstanding = BalanceCalculator.outstandingByCurrency(balanceList, currencyByProject),
            owedClerks = owedClerks,
            isLoaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    companion object {
        fun factory(
            projectDao: ProjectDao,
            clerkDao: ClerkDao,
            attendanceEntryDao: AttendanceEntryDao,
            extraPayLineDao: ExtraPayLineDao,
            paymentDao: PaymentDao,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(projectDao, clerkDao, attendanceEntryDao, extraPayLineDao, paymentDao) }
        }
    }
}
