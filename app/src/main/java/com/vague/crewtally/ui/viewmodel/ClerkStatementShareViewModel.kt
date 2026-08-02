package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.data.local.AttendanceEntryDao
import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ClerkDao
import com.vague.crewtally.data.local.CompanyDao
import com.vague.crewtally.data.local.ExtraPayLineDao
import com.vague.crewtally.data.local.ExtraPayLineWithDate
import com.vague.crewtally.data.local.PaymentDao
import com.vague.crewtally.data.local.PaymentEntity
import com.vague.crewtally.data.local.ProjectDao
import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.report.ClerkStatement
import com.vague.crewtally.report.ClerkStatementBuilder
import com.vague.crewtally.report.ReportFileNames
import com.vague.crewtally.report.ReportFileWriter
import com.vague.crewtally.report.ReportLines
import com.vague.crewtally.report.ReportStrings
import com.vague.crewtally.report.TextReportRenderer
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A file ready to hand to the system share sheet. */
data class ShareFileRequest(val file: File, val mimeType: String)

data class ClerkStatementShareUiState(
    val statement: ClerkStatement? = null,
    val isLoaded: Boolean = false,
    val isGenerating: Boolean = false,
    val generationFailure: Boolean = false,
)

sealed interface ClerkStatementShareEvent {
    data object ShareAsText : ClerkStatementShareEvent
    data object ShareAsPdf : ClerkStatementShareEvent
    data object DismissGenerationFailure : ClerkStatementShareEvent
}

/**
 * Backs the clerk statement share screen (LOCKED Phase 5): builds the [ClerkStatement] reactively
 * from the same ledger flows [ClerkBalanceViewModel] uses (so the preview totals can never
 * disagree with the balance screen), then generates the requested file off the main thread on
 * demand. [isGenerating] doubles as the re-entrancy guard — a second tap while a file is already
 * being written is a no-op rather than a second concurrent write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClerkStatementShareViewModel(
    private val projectId: String,
    private val clerkId: String,
    projectDao: ProjectDao,
    clerkDao: ClerkDao,
    companyDao: CompanyDao,
    attendanceEntryDao: AttendanceEntryDao,
    extraPayLineDao: ExtraPayLineDao,
    paymentDao: PaymentDao,
    private val fileWriter: ReportFileWriter,
    private val strings: ReportStrings,
    /** Overridable only so tests can substitute a deterministic test dispatcher for the real [Dispatchers.IO]. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private data class RawLedger(
        val project: ProjectEntity?,
        val clerkName: String,
        val attendance: List<AttendanceEntryEntity>,
        val extras: List<ExtraPayLineWithDate>,
        val payments: List<PaymentEntity>,
    )

    private val ledger: Flow<RawLedger> = combine(
        projectDao.observeById(projectId),
        clerkDao.observeById(clerkId),
        attendanceEntryDao.observeForClerkOnProject(projectId, clerkId),
        extraPayLineDao.observeForClerkOnProject(projectId, clerkId),
        paymentDao.observeForClerkOnProject(projectId, clerkId),
    ) { project, clerk, attendance, extras, payments ->
        RawLedger(project, clerk?.name.orEmpty(), attendance, extras, payments)
    }

    private val companyName: Flow<String> = projectDao.observeById(projectId)
        .map { it?.companyId }
        .distinctUntilChanged()
        .flatMapLatest { companyId -> companyId?.let(companyDao::observeById) ?: flowOf(null) }
        .map { it?.name.orEmpty() }

    private val statementState: StateFlow<ClerkStatement?> = combine(ledger, companyName) { raw, company ->
        if (raw.project == null) return@combine null
        ClerkStatementBuilder.build(
            clerkName = raw.clerkName,
            projectName = raw.project.name,
            companyName = company,
            currency = raw.project.currency,
            attendance = raw.attendance,
            extras = raw.extras,
            payments = raw.payments,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val _isGenerating = MutableStateFlow(false)
    private val _generationFailure = MutableStateFlow(false)

    val uiState: StateFlow<ClerkStatementShareUiState> =
        combine(statementState, _isGenerating, _generationFailure) { statement, generating, failure ->
            ClerkStatementShareUiState(
                statement = statement,
                isLoaded = statement != null,
                isGenerating = generating,
                generationFailure = failure,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            ClerkStatementShareUiState(),
        )

    private val _shareRequests = MutableSharedFlow<ShareFileRequest>(extraBufferCapacity = 1)
    val shareRequests: SharedFlow<ShareFileRequest> = _shareRequests

    fun onEvent(event: ClerkStatementShareEvent) {
        when (event) {
            ClerkStatementShareEvent.DismissGenerationFailure -> _generationFailure.value = false
            ClerkStatementShareEvent.ShareAsText -> shareAsText()
            ClerkStatementShareEvent.ShareAsPdf -> shareAsPdf()
        }
    }

    private fun shareAsText() {
        val statement = statementState.value ?: return
        if (_isGenerating.value) return // re-entrancy guard: ignore a second tap while generating.
        generate {
            val content = TextReportRenderer.renderClerkStatement(statement, strings)
            val fileName = ReportFileNames.clerkStatementFileName(statement.clerkName, "txt")
            ShareFileRequest(fileWriter.writeText(fileName, content), "text/plain")
        }
    }

    private fun shareAsPdf() {
        val statement = statementState.value ?: return
        if (_isGenerating.value) return // re-entrancy guard: ignore a second tap while generating.
        generate {
            val lines = ReportLines.forClerkStatement(statement, strings)
            val fileName = ReportFileNames.clerkStatementFileName(statement.clerkName, "pdf")
            ShareFileRequest(fileWriter.writePdf(fileName, lines, strings.pageLabelTemplate), "application/pdf")
        }
    }

    /**
     * Runs [block] off the main thread and shares the result. An IO failure here (disk full,
     * PDF drawing error) used to leave `isGenerating` stuck true forever with no way for the
     * user to retry — now it resets in a `finally` block regardless of outcome, and a caught
     * failure surfaces `generationFailure` so the screen can show a dismissable alert instead
     * of dead buttons.
     */
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
            projectId: String,
            clerkId: String,
            projectDao: ProjectDao,
            clerkDao: ClerkDao,
            companyDao: CompanyDao,
            attendanceEntryDao: AttendanceEntryDao,
            extraPayLineDao: ExtraPayLineDao,
            paymentDao: PaymentDao,
            fileWriter: ReportFileWriter,
            strings: ReportStrings,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ClerkStatementShareViewModel(
                    projectId,
                    clerkId,
                    projectDao,
                    clerkDao,
                    companyDao,
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
