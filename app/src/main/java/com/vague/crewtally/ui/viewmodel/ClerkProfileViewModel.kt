package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.balance.BalanceCalculator
import com.vague.crewtally.balance.CurrencyTotal
import com.vague.crewtally.data.local.AttendanceEntryDao
import com.vague.crewtally.data.local.ClerkDao
import com.vague.crewtally.data.local.ExtraPayLineDao
import com.vague.crewtally.data.local.PaymentDao
import com.vague.crewtally.data.local.ProjectDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One per-project row on a clerk's profile: the project's name, currency, and this clerk's owed there. */
data class ProfileProjectRow(
    val projectId: String,
    val projectName: String,
    val currency: String,
    val owed: Long,
)

data class ClerkProfileUiState(
    val clerkName: String = "",
    /** Earned / paid / owed grouped per currency (never summed across currencies). */
    val currencyTotals: List<CurrencyTotal> = emptyList(),
    /** Per-project rows, most owed first, each linking to that project's balance screen. */
    val projectRows: List<ProfileProjectRow> = emptyList(),
    val isLoaded: Boolean = false,
)

/**
 * Backs the clerk profile (Clerks tab → clerk): the clerk's cross-project totals grouped per
 * currency and a per-project row list, both derived from the clerk-scoped money roll-ups
 * (nothing stored). The profile is view-first; editing the clerk stays on the existing edit
 * form, reached from the header.
 */
class ClerkProfileViewModel(
    clerkId: String,
    clerkDao: ClerkDao,
    projectDao: ProjectDao,
    attendanceEntryDao: AttendanceEntryDao,
    extraPayLineDao: ExtraPayLineDao,
    paymentDao: PaymentDao,
) : ViewModel() {

    private val balances = combine(
        attendanceEntryDao.observeEarningsRollupForClerk(clerkId),
        extraPayLineDao.observeExtrasRollupForClerk(clerkId),
        paymentDao.observePaymentsRollupForClerk(clerkId),
    ) { earnings, extras, payments -> BalanceCalculator.rollup(earnings, extras, payments) }

    val uiState: StateFlow<ClerkProfileUiState> = combine(
        clerkDao.observeById(clerkId),
        projectDao.observeAll(),
        balances,
    ) { clerk, projects, balanceList ->
        val currencyByProject = projects.associate { it.id to it.currency }
        val projectNameById = projects.associate { it.id to it.name }

        val projectRows = balanceList
            .map { balance ->
                ProfileProjectRow(
                    projectId = balance.projectId,
                    projectName = projectNameById[balance.projectId].orEmpty(),
                    currency = currencyByProject[balance.projectId].orEmpty(),
                    owed = balance.owed,
                )
            }
            .sortedByDescending { it.owed }

        ClerkProfileUiState(
            clerkName = clerk?.name.orEmpty(),
            currencyTotals = BalanceCalculator.currencyTotals(balanceList, currencyByProject),
            projectRows = projectRows,
            isLoaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ClerkProfileUiState())

    companion object {
        fun factory(
            clerkId: String,
            clerkDao: ClerkDao,
            projectDao: ProjectDao,
            attendanceEntryDao: AttendanceEntryDao,
            extraPayLineDao: ExtraPayLineDao,
            paymentDao: PaymentDao,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ClerkProfileViewModel(clerkId, clerkDao, projectDao, attendanceEntryDao, extraPayLineDao, paymentDao)
            }
        }
    }
}
