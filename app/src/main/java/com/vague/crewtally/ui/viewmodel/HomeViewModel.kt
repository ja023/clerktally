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

/**
 * One currency's group of owed-clerk rows on the Home dashboard, sorted by amount owed
 * descending WITHIN the group. Rows are never sorted flat across currencies — LBP minor units
 * and USD minor units are not the same money, so comparing their raw [OwedClerkRow.owed]
 * against each other is meaningless (LOCKED: cross-currency amounts are never compared). Group
 * order matches [HomeUiState.outstanding]'s order, so the two sections read as one consistent
 * per-currency story.
 */
data class OwedClerkGroup(
    val currency: String,
    val clerks: List<OwedClerkRow>,
)

data class HomeUiState(
    /** Active projects, each with a "Take attendance" shortcut (name + company). */
    val activeProjects: List<ProjectSummary> = emptyList(),
    /** Outstanding total per currency (positive balances only, never summed across currencies). */
    val outstanding: List<OutstandingTotal> = emptyList(),
    /** Clerks who are owed money, grouped per currency (never flat-sorted across currencies). */
    val owedClerkGroups: List<OwedClerkGroup> = emptyList(),
    val isLoaded: Boolean = false,
)

/**
 * Backs the Home tab — the projects-first dashboard (LOCKED Phase 4). Active projects come
 * straight from the project summaries; the outstanding totals and owed-clerks list are derived
 * by folding the three money roll-ups into per-(project, clerk) balances (nothing stored) and
 * joining them with project and clerk names. Balances are drawn across ALL projects, not just
 * active ones — a completed project can still have open books. Cross-currency amounts are never
 * summed OR compared (LOCKED); each currency stands alone, both in the outstanding totals and in
 * the owed-clerks groups.
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

        val outstanding = BalanceCalculator.outstandingByCurrency(balanceList, currencyByProject)

        // Group per currency first, THEN sort descending within each group — the flat
        // cross-currency sort this replaces compared LBP minor units against USD minor units
        // as if they were the same money. Group order mirrors [outstanding] so both sections
        // agree on which currency comes first.
        val owedRowsByCurrency = balanceList
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
            .groupBy { it.currency }
        val owedClerkGroups = outstanding.map { total ->
            OwedClerkGroup(
                currency = total.currency,
                clerks = owedRowsByCurrency[total.currency].orEmpty().sortedByDescending { it.owed },
            )
        }

        HomeUiState(
            activeProjects = activeProjects,
            outstanding = outstanding,
            owedClerkGroups = owedClerkGroups,
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
