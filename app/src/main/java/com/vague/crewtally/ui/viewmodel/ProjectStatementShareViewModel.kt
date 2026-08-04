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
import com.vague.crewtally.data.local.ExtraPayLineWithClerk
import com.vague.crewtally.data.local.ExtraPayLineWithDate
import com.vague.crewtally.data.local.PaymentDao
import com.vague.crewtally.data.local.PaymentEntity
import com.vague.crewtally.data.local.ProjectDao
import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.report.ProjectStatement
import com.vague.crewtally.report.ProjectStatementBuilder
import com.vague.crewtally.report.ReportClerkLedgerInput
import com.vague.crewtally.report.ReportDateRange
import com.vague.crewtally.report.ReportDateRanges
import com.vague.crewtally.report.ReportFileNames
import com.vague.crewtally.report.ReportFileWriter
import com.vague.crewtally.report.ReportLines
import com.vague.crewtally.report.ReportRangePreset
import com.vague.crewtally.report.ReportStrings
import com.vague.crewtally.report.TextReportRenderer
import java.time.LocalDate
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

data class ProjectStatementShareUiState(
    val statement: ProjectStatement? = null,
    /** The project's identity, available as soon as the project itself loads — independent of
     *  [statement], which is null both while loading AND while [isRangeInvalid] (no statement to
     *  build), so the screen can still show a header while showing the invalid-range error. */
    val projectName: String = "",
    val companyName: String = "",
    val currency: String = "",
    val isLoaded: Boolean = false,
    val isGenerating: Boolean = false,
    val generationFailure: Boolean = false,
    val rangePreset: ReportRangePreset = ReportRangePreset.ALL_TIME,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    /** True when a CUSTOM range has both dates set with From after To — see [RangeSelection.isInvalid]. */
    val isRangeInvalid: Boolean = false,
)

sealed interface ProjectStatementShareEvent {
    data class RangePresetChanged(val preset: ReportRangePreset) : ProjectStatementShareEvent
    data class CustomStartChanged(val date: LocalDate) : ProjectStatementShareEvent
    data class CustomEndChanged(val date: LocalDate) : ProjectStatementShareEvent
    data object ShareAsText : ProjectStatementShareEvent
    data object ShareAsPdf : ProjectStatementShareEvent
    data object DismissGenerationFailure : ProjectStatementShareEvent
}

/**
 * Backs the NEW v1.1 project statement share screen: a per-clerk summary table (days/earned/
 * paid/owed) plus a PROJECT TOTAL row, for every clerk who ever had activity on the project,
 * scoped to the selected [ReportDateRange] (LOCKED default All time). Draws from the same
 * whole-book raw flows [CompanyReportShareViewModel] uses (attendance/extras/payments), filtered
 * down to this one project — changing the range recomputes [statementState] reactively so the
 * preview always matches what would be shared.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectStatementShareViewModel(
    private val projectId: String,
    projectDao: ProjectDao,
    companyDao: CompanyDao,
    clerkDao: ClerkDao,
    attendanceEntryDao: AttendanceEntryDao,
    extraPayLineDao: ExtraPayLineDao,
    paymentDao: PaymentDao,
    private val fileWriter: ReportFileWriter,
    private val strings: ReportStrings,
    private val today: LocalDate = LocalDate.now(),
    /** Overridable only so tests can substitute a deterministic test dispatcher for the real [Dispatchers.IO]. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private data class Identity(val project: ProjectEntity?, val companyName: String, val clerkNameById: Map<String, String>)

    private data class RawData(
        val project: ProjectEntity?,
        val companyName: String,
        val clerkNameById: Map<String, String>,
        val attendance: List<AttendanceEntryEntity>,
        val extras: List<ExtraPayLineWithClerk>,
        val payments: List<PaymentEntity>,
    )

    private data class RangeSelection(val preset: ReportRangePreset, val customStart: LocalDate?, val customEnd: LocalDate?) {
        /** A CUSTOM range with both dates set but From after To — nothing to build, no statement. */
        val isInvalid: Boolean
            get() = preset == ReportRangePreset.CUSTOM &&
                customStart != null && customEnd != null && customStart.isAfter(customEnd)
    }

    private val companyName: Flow<String> = projectDao.observeById(projectId)
        .map { it?.companyId }
        .distinctUntilChanged()
        .flatMapLatest { companyId -> companyId?.let(companyDao::observeById) ?: flowOf(null) }
        .map { it?.name.orEmpty() }

    private val identity: Flow<Identity> = combine(
        projectDao.observeById(projectId),
        companyName,
        clerkDao.observeAll(),
    ) { project, company, clerks -> Identity(project, company, clerks.associate { it.id to it.name }) }

    private val raw: Flow<RawData> = combine(
        identity,
        attendanceEntryDao.observeAll(),
        extraPayLineDao.observeAllWithClerk(),
        paymentDao.observeAll(),
    ) { id, attendance, extras, payments ->
        RawData(
            project = id.project,
            companyName = id.companyName,
            clerkNameById = id.clerkNameById,
            attendance = attendance.filter { it.projectId == projectId },
            extras = extras.filter { it.projectId == projectId },
            payments = payments.filter { it.projectId == projectId },
        )
    }

    private val _rangePreset = MutableStateFlow(ReportRangePreset.ALL_TIME)
    private val _customStart = MutableStateFlow<LocalDate?>(null)
    private val _customEnd = MutableStateFlow<LocalDate?>(null)

    private val rangeSelection: StateFlow<RangeSelection> =
        combine(_rangePreset, _customStart, _customEnd) { preset, start, end -> RangeSelection(preset, start, end) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RangeSelection(ReportRangePreset.ALL_TIME, null, null))

    private data class ProjectHeader(val projectName: String, val companyName: String, val currency: String)

    private val header: StateFlow<ProjectHeader?> = raw
        .map { data -> data.project?.let { ProjectHeader(it.name, data.companyName, it.currency) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val statementState: StateFlow<ProjectStatement?> = combine(raw, rangeSelection) { data, selection ->
        val project = data.project ?: return@combine null
        // Invalid range (From after To): nothing to build — the screen shows [ProjectStatementShareUiState.isRangeInvalid]
        // instead of a statement, rather than silently rendering the generic empty-activity state.
        if (selection.isInvalid) return@combine null
        val clerkIds = (data.attendance.map { it.clerkId } + data.extras.map { it.clerkId } + data.payments.map { it.clerkId }).toSet()
        val clerkInputs = clerkIds.map { clerkId ->
            ReportClerkLedgerInput(
                clerkId = clerkId,
                clerkName = data.clerkNameById[clerkId].orEmpty(),
                attendance = data.attendance.filter { it.clerkId == clerkId },
                extras = data.extras.filter { it.clerkId == clerkId }.map { ExtraPayLineWithDate(it.line, it.date) },
                payments = data.payments.filter { it.clerkId == clerkId },
            )
        }
        ProjectStatementBuilder.build(
            projectName = project.name,
            companyName = data.companyName,
            currency = project.currency,
            clerks = clerkInputs,
            range = ReportDateRanges.resolve(selection.preset, today, selection.customStart, selection.customEnd),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val _isGenerating = MutableStateFlow(false)
    private val _generationFailure = MutableStateFlow(false)

    val uiState: StateFlow<ProjectStatementShareUiState> =
        combine(statementState, header, _isGenerating, _generationFailure, rangeSelection) { statement, projectHeader, generating, failure, selection ->
            ProjectStatementShareUiState(
                statement = statement,
                projectName = projectHeader?.projectName.orEmpty(),
                companyName = projectHeader?.companyName.orEmpty(),
                currency = projectHeader?.currency.orEmpty(),
                isLoaded = projectHeader != null,
                isGenerating = generating,
                generationFailure = failure,
                rangePreset = selection.preset,
                customStart = selection.customStart,
                customEnd = selection.customEnd,
                isRangeInvalid = selection.isInvalid,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ProjectStatementShareUiState())

    private val _shareRequests = MutableSharedFlow<ShareFileRequest>(extraBufferCapacity = 1)
    val shareRequests: SharedFlow<ShareFileRequest> = _shareRequests

    fun onEvent(event: ProjectStatementShareEvent) {
        when (event) {
            is ProjectStatementShareEvent.RangePresetChanged -> onRangePresetChanged(event.preset)
            is ProjectStatementShareEvent.CustomStartChanged -> _customStart.value = event.date
            is ProjectStatementShareEvent.CustomEndChanged -> _customEnd.value = event.date
            ProjectStatementShareEvent.DismissGenerationFailure -> _generationFailure.value = false
            ProjectStatementShareEvent.ShareAsText -> shareAsText()
            ProjectStatementShareEvent.ShareAsPdf -> shareAsPdf()
        }
    }

    /**
     * Selecting CUSTOM must make the displayed date fields and the applied filter agree from
     * the first frame: [ReportDateRanges.resolve] treats null/null bounds as All time, but the
     * screen pre-fills empty custom fields with `today` for display — so leaving state null
     * would show "today - today" while silently applying All time. Defaulting state itself to
     * today (only if unset) makes state the single source of truth for both.
     */
    private fun onRangePresetChanged(preset: ReportRangePreset) {
        _rangePreset.value = preset
        if (preset == ReportRangePreset.CUSTOM) {
            if (_customStart.value == null) _customStart.value = today
            if (_customEnd.value == null) _customEnd.value = today
        }
    }

    private fun shareAsText() {
        val statement = statementState.value ?: return
        if (_isGenerating.value) return // re-entrancy guard: ignore a second tap while generating.
        generate {
            val content = TextReportRenderer.renderProjectStatement(statement, strings)
            val fileName = ReportFileNames.projectStatementFileName(statement.projectName, "txt")
            ShareFileRequest(fileWriter.writeText(fileName, content), "text/plain")
        }
    }

    private fun shareAsPdf() {
        val statement = statementState.value ?: return
        if (_isGenerating.value) return // re-entrancy guard: ignore a second tap while generating.
        generate {
            val lines = ReportLines.forProjectStatement(statement, strings)
            val fileName = ReportFileNames.projectStatementFileName(statement.projectName, "pdf")
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
            projectId: String,
            projectDao: ProjectDao,
            companyDao: CompanyDao,
            clerkDao: ClerkDao,
            attendanceEntryDao: AttendanceEntryDao,
            extraPayLineDao: ExtraPayLineDao,
            paymentDao: PaymentDao,
            fileWriter: ReportFileWriter,
            strings: ReportStrings,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProjectStatementShareViewModel(
                    projectId,
                    projectDao,
                    companyDao,
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
