package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.balance.BalanceCalculator
import com.vague.crewtally.data.local.AttendanceEntryDao
import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ClerkDao
import com.vague.crewtally.data.local.ExtraPayLineDao
import com.vague.crewtally.data.local.ExtraPayLineWithDate
import com.vague.crewtally.data.local.PaymentDao
import com.vague.crewtally.data.local.PaymentEntity
import com.vague.crewtally.data.local.ProjectDao
import com.vague.crewtally.data.local.RosterEntryDao
import com.vague.crewtally.data.local.RosterEntryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The clerk balance screen's state: the derived owed figure (never stored) plus the full ledger
 * it breaks down into — present days with their rate snapshots, signed extras lines, and
 * payments. Currency is the project's cosmetic symbol source.
 */
data class ClerkBalanceUiState(
    val clerkName: String = "",
    val currency: String = "",
    val earned: Long = 0L,
    val extrasTotal: Long = 0L,
    val paid: Long = 0L,
    /** Present-day earning rows (date, rate), oldest first. */
    val presentDays: List<AttendanceEntryEntity> = emptyList(),
    /** Signed extra-pay lines with their day, oldest first. */
    val extraLines: List<ExtraPayLineWithDate> = emptyList(),
    /** Payments, newest first (DAO-sorted). */
    val payments: List<PaymentEntity> = emptyList(),
    val isLoaded: Boolean = false,
) {
    /** owed = earned + extras − paid. Negative reads as an advance; recomputed, never stored. */
    val owed: Long get() = BalanceCalculator.owed(earned, extrasTotal, paid)
}

/**
 * Backs the per-project clerk balance screen. The owed figure and every ledger section are
 * derived by [combine]-ing the clerk's attendance, extras, and payment flows for this project,
 * so a payment recorded elsewhere (the payment form pops back here) updates the visible owed
 * figure reactively with no manual refresh. Nothing here stores a balance.
 */
class ClerkBalanceViewModel(
    private val projectId: String,
    private val clerkId: String,
    projectDao: ProjectDao,
    clerkDao: ClerkDao,
    rosterEntryDao: RosterEntryDao,
    attendanceEntryDao: AttendanceEntryDao,
    extraPayLineDao: ExtraPayLineDao,
    paymentDao: PaymentDao,
) : ViewModel() {

    /**
     * The clerk's active roster row on this project, if any — drives the "Edit daily rate"
     * secondary action (a removed roster clerk or a day-only walk-in has none, so no rate edit).
     */
    val rosterRow: StateFlow<RosterEntryEntity?> =
        rosterEntryDao.observeActiveRosterForProject(projectId)
            .map { rows -> rows.firstOrNull { it.entry.clerkId == clerkId }?.entry }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val uiState: StateFlow<ClerkBalanceUiState> = combine(
        projectDao.observeById(projectId),
        clerkDao.observeById(clerkId),
        attendanceEntryDao.observeForClerkOnProject(projectId, clerkId),
        extraPayLineDao.observeForClerkOnProject(projectId, clerkId),
        paymentDao.observeForClerkOnProject(projectId, clerkId),
    ) { project, clerk, attendance, extras, payments ->
        ClerkBalanceUiState(
            clerkName = clerk?.name.orEmpty(),
            currency = project?.currency.orEmpty(),
            earned = BalanceCalculator.earnedFrom(attendance),
            extrasTotal = extras.sumOf { it.line.amount },
            paid = BalanceCalculator.paidFrom(payments),
            presentDays = attendance.filter { it.present },
            extraLines = extras,
            payments = payments,
            isLoaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ClerkBalanceUiState())

    companion object {
        fun factory(
            projectId: String,
            clerkId: String,
            projectDao: ProjectDao,
            clerkDao: ClerkDao,
            rosterEntryDao: RosterEntryDao,
            attendanceEntryDao: AttendanceEntryDao,
            extraPayLineDao: ExtraPayLineDao,
            paymentDao: PaymentDao,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ClerkBalanceViewModel(
                    projectId,
                    clerkId,
                    projectDao,
                    clerkDao,
                    rosterEntryDao,
                    attendanceEntryDao,
                    extraPayLineDao,
                    paymentDao,
                )
            }
        }
    }
}
