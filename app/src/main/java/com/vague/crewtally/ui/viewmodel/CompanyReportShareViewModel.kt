package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.balance.BalanceCalculator
import com.vague.crewtally.data.local.AttendanceEntryDao
import com.vague.crewtally.data.local.ClerkDao
import com.vague.crewtally.data.local.CompanyDao
import com.vague.crewtally.data.local.ExtraPayLineDao
import com.vague.crewtally.data.local.PaymentDao
import com.vague.crewtally.data.local.ProjectDao
import com.vague.crewtally.report.CompanyReportClerkInput
import com.vague.crewtally.report.CompanyReportProjectInput
import com.vague.crewtally.report.CompanyTotals
import com.vague.crewtally.report.CompanyTotalsBuilder
import com.vague.crewtally.report.ReportFileNames
import com.vague.crewtally.report.ReportFileWriter
import com.vague.crewtally.report.ReportLines
import com.vague.crewtally.report.ReportStrings
import com.vague.crewtally.report.TextReportRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CompanyReportShareUiState(
    val totals: CompanyTotals? = null,
    val isLoaded: Boolean = false,
    val isGenerating: Boolean = false,
    val generationFailure: Boolean = false,
)

sealed interface CompanyReportShareEvent {
    data object ShareAsText : CompanyReportShareEvent
    data object ShareAsPdf : CompanyReportShareEvent
    data object DismissGenerationFailure : CompanyReportShareEvent
}

/**
 * Backs the company report share screen (LOCKED Phase 5, report #2). Draws from the SAME
 * whole-book money roll-ups [HomeViewModel] uses, filtered down to this company's projects —
 * every clerk on every one of the company's projects, broken down and grand-totaled per currency
 * (never summed across currencies). Generation is off-thread with the same re-entrancy guard as
 * [ClerkStatementShareViewModel].
 */
class CompanyReportShareViewModel(
    private val companyId: String,
    companyDao: CompanyDao,
    projectDao: ProjectDao,
    clerkDao: ClerkDao,
    attendanceEntryDao: AttendanceEntryDao,
    extraPayLineDao: ExtraPayLineDao,
    paymentDao: PaymentDao,
    private val fileWriter: ReportFileWriter,
    private val strings: ReportStrings,
    /** Overridable only so tests can substitute a deterministic test dispatcher for the real [Dispatchers.IO]. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val balances = combine(
        attendanceEntryDao.observeEarningsRollup(),
        extraPayLineDao.observeExtrasRollup(),
        paymentDao.observePaymentsRollup(),
    ) { earnings, extras, payments -> BalanceCalculator.rollup(earnings, extras, payments) }

    private val totalsState: StateFlow<CompanyTotals?> = combine(
        companyDao.observeById(companyId),
        projectDao.observeByCompany(companyId),
        clerkDao.observeAll(),
        balances,
    ) { company, projects, clerks, balanceList ->
        if (company == null) return@combine null
        val clerkNameById = clerks.associate { it.id to it.name }
        val projectInputs = projects.map { project ->
            val projectBalances = balanceList.filter { it.projectId == project.id }
            CompanyReportProjectInput(
                projectId = project.id,
                projectName = project.name,
                currency = project.currency,
                clerks = projectBalances.map { balance ->
                    CompanyReportClerkInput(
                        clerkId = balance.clerkId,
                        clerkName = clerkNameById[balance.clerkId].orEmpty(),
                        earned = balance.earned,
                        extras = balance.extras,
                        paid = balance.paid,
                    )
                },
            )
        }
        CompanyTotalsBuilder.build(company.name, projectInputs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val _isGenerating = MutableStateFlow(false)
    private val _generationFailure = MutableStateFlow(false)

    val uiState: StateFlow<CompanyReportShareUiState> =
        combine(totalsState, _isGenerating, _generationFailure) { totals, generating, failure ->
            CompanyReportShareUiState(
                totals = totals,
                isLoaded = totals != null,
                isGenerating = generating,
                generationFailure = failure,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CompanyReportShareUiState())

    private val _shareRequests = MutableSharedFlow<ShareFileRequest>(extraBufferCapacity = 1)
    val shareRequests: SharedFlow<ShareFileRequest> = _shareRequests

    fun onEvent(event: CompanyReportShareEvent) {
        when (event) {
            CompanyReportShareEvent.DismissGenerationFailure -> _generationFailure.value = false
            CompanyReportShareEvent.ShareAsText -> shareAsText()
            CompanyReportShareEvent.ShareAsPdf -> shareAsPdf()
        }
    }

    private fun shareAsText() {
        val totals = totalsState.value ?: return
        if (_isGenerating.value) return // re-entrancy guard: ignore a second tap while generating.
        generate {
            val content = TextReportRenderer.renderCompanyTotals(totals, strings)
            val fileName = ReportFileNames.companyReportFileName(totals.companyName, "txt")
            ShareFileRequest(fileWriter.writeText(fileName, content), "text/plain")
        }
    }

    private fun shareAsPdf() {
        val totals = totalsState.value ?: return
        if (_isGenerating.value) return // re-entrancy guard: ignore a second tap while generating.
        generate {
            val lines = ReportLines.forCompanyTotals(totals, strings)
            val fileName = ReportFileNames.companyReportFileName(totals.companyName, "pdf")
            ShareFileRequest(fileWriter.writePdf(fileName, lines, strings.pageLabelTemplate), "application/pdf")
        }
    }

    /** See [ClerkStatementShareViewModel.generate]'s KDoc — same failure-handling shape. */
    private fun generate(block: suspend () -> ShareFileRequest) {
        _isGenerating.value = true
        viewModelScope.launch {
            try {
                val request = withContext(ioDispatcher) { block() }
                _shareRequests.emit(request)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                _generationFailure.value = true
            } finally {
                _isGenerating.value = false
            }
        }
    }

    companion object {
        fun factory(
            companyId: String,
            companyDao: CompanyDao,
            projectDao: ProjectDao,
            clerkDao: ClerkDao,
            attendanceEntryDao: AttendanceEntryDao,
            extraPayLineDao: ExtraPayLineDao,
            paymentDao: PaymentDao,
            fileWriter: ReportFileWriter,
            strings: ReportStrings,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CompanyReportShareViewModel(
                    companyId,
                    companyDao,
                    projectDao,
                    clerkDao,
                    attendanceEntryDao,
                    extraPayLineDao,
                    paymentDao,
                    fileWriter,
                    strings,
                )
            }
        }
    }
}
