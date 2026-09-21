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
import com.vague.crewtally.report.ClerkMultiProjectStatement
import com.vague.crewtally.report.ClerkMultiProjectStatementBuilder
import com.vague.crewtally.report.ClerkProjectBucket
import com.vague.crewtally.report.ClerkProjectLedgerInput
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ClerkMultiProjectStatementShareUiState(
    val statement: ClerkMultiProjectStatement? = null,
    val clerkName: String = "",
    val bucket: ClerkProjectBucket = ClerkProjectBucket.ACTIVE,
    val isLoaded: Boolean = false,
    /**
     * How many projects sit in this bucket for this clerk BEFORE the range filter — the screen
     * needs it to tell "this clerk has no active projects at all" apart from "they have some,
     * but nothing happened inside the selected range", which are different messages.
     */
    val bucketProjectCount: Int = 0,
    val isGenerating: Boolean = false,
    val generationFailure: Boolean = false,
    val rangePreset: ReportRangePreset = ReportRangePreset.ALL_TIME,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val isRangeInvalid: Boolean = false,
) {
    /** Whether there is anything at all to render or share (an empty bucket offers nothing). */
    val hasContent: Boolean get() = statement != null && statement.sections.isNotEmpty()
}

sealed interface ClerkMultiProjectStatementShareEvent {
    data class RangePresetChanged(val preset: ReportRangePreset) : ClerkMultiProjectStatementShareEvent
    data class CustomStartChanged(val date: LocalDate) : ClerkMultiProjectStatementShareEvent
    data class CustomEndChanged(val date: LocalDate) : ClerkMultiProjectStatementShareEvent
    data object ShareAsText : ClerkMultiProjectStatementShareEvent
    data object ShareAsPdf : ClerkMultiProjectStatementShareEvent
    data object DismissGenerationFailure : ClerkMultiProjectStatementShareEvent
}

/**
 * Backs the v1.2 cross-project clerk statement share screens (one instance per
 * [ClerkProjectBucket]). Same shape as [CompanyReportShareViewModel]: whole-book raw flows
 * filtered down to this clerk, a reactive range selection, off-thread generation with a
 * re-entrancy guard, and a [shareRequests] one-shot channel to the system share sheet.
 *
 * Attendance and extras come from the whole-book `observeAll` flows and are narrowed to this
 * clerk in memory (matching the app-wide convention noted in the v1.1 review debt) because no
 * clerk-scoped RAW query exists — the clerk-scoped DAO queries are pre-summed roll-ups, and this
 * report needs day-level rows.
 */
class ClerkMultiProjectStatementShareViewModel(
    private val clerkId: String,
    private val bucket: ClerkProjectBucket,
    clerkDao: ClerkDao,
    projectDao: ProjectDao,
    companyDao: CompanyDao,
    attendanceEntryDao: AttendanceEntryDao,
    extraPayLineDao: ExtraPayLineDao,
    paymentDao: PaymentDao,
    private val fileWriter: ReportFileWriter,
    private val strings: ReportStrings,
    private val today: LocalDate = LocalDate.now(),
    /** Overridable only so tests can substitute a deterministic test dispatcher for the real [Dispatchers.IO]. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private data class Identity(
        val clerkName: String?,
        val projects: List<ProjectEntity>,
        val companyNameById: Map<String, String>,
    )

    private data class RawData(
        val identity: Identity,
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

    private val identity: Flow<Identity> = combine(
        clerkDao.observeById(clerkId),
        projectDao.observeAll(),
        companyDao.observeAll(),
    ) { clerk, projects, companies ->
        Identity(
            clerkName = clerk?.name,
            projects = projects.filter { bucket.includes(it.status) },
            companyNameById = companies.associate { it.id to it.name },
        )
    }

    private val raw: Flow<RawData> = combine(
        identity,
        attendanceEntryDao.observeAll(),
        extraPayLineDao.observeAllWithClerk(),
        paymentDao.observeByClerk(clerkId),
    ) { id, attendance, extras, payments ->
        RawData(
            identity = id,
            attendance = attendance.filter { it.clerkId == clerkId },
            extras = extras.filter { it.clerkId == clerkId },
            payments = payments,
        )
    }

    private val _rangePreset = MutableStateFlow(ReportRangePreset.ALL_TIME)
    private val _customStart = MutableStateFlow<LocalDate?>(null)
    private val _customEnd = MutableStateFlow<LocalDate?>(null)

    private val rangeSelection: StateFlow<RangeSelection> =
        combine(_rangePreset, _customStart, _customEnd) { preset, start, end -> RangeSelection(preset, start, end) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                RangeSelection(ReportRangePreset.ALL_TIME, null, null),
            )

    private val clerkName: StateFlow<String?> =
        raw.map { it.identity.clerkName }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Projects in this bucket where the clerk has ANY ledger row, ignoring the range filter. */
    private val bucketProjectCount: StateFlow<Int> = raw
        .map { data -> projectInputs(data).count { it.attendance.isNotEmpty() || it.extras.isNotEmpty() || it.payments.isNotEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    private val statementState: StateFlow<ClerkMultiProjectStatement?> = combine(raw, rangeSelection) { data, selection ->
        val name = data.identity.clerkName ?: return@combine null
        // Invalid range (From after To): nothing to build — the screen shows the range error
        // instead of a statement, rather than silently rendering the generic empty state.
        if (selection.isInvalid) return@combine null
        val range = ReportDateRanges.resolve(selection.preset, today, selection.customStart, selection.customEnd)
        ClerkMultiProjectStatementBuilder.build(
            clerkName = name,
            bucket = bucket,
            projects = projectInputs(data),
            range = range,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** One [ClerkProjectLedgerInput] per bucket project, carrying this clerk's rows on it. */
    private fun projectInputs(data: RawData): List<ClerkProjectLedgerInput> = data.identity.projects.map { project ->
        ClerkProjectLedgerInput(
            projectId = project.id,
            projectName = project.name,
            companyName = data.identity.companyNameById[project.companyId].orEmpty(),
            currency = project.currency,
            status = project.status,
            attendance = data.attendance.filter { it.projectId == project.id },
            extras = data.extras.filter { it.projectId == project.id }.map { ExtraPayLineWithDate(it.line, it.date) },
            payments = data.payments.filter { it.projectId == project.id },
        )
    }

    private val _isGenerating = MutableStateFlow(false)
    private val _generationFailure = MutableStateFlow(false)

    private val generationState: Flow<Pair<Boolean, Boolean>> =
        combine(_isGenerating, _generationFailure) { generating, failure -> generating to failure }

    val uiState: StateFlow<ClerkMultiProjectStatementShareUiState> = combine(
        statementState,
        clerkName,
        bucketProjectCount,
        generationState,
        rangeSelection,
    ) { statement, name, projectCount, generation, selection ->
        ClerkMultiProjectStatementShareUiState(
            statement = statement,
            clerkName = name.orEmpty(),
            bucket = bucket,
            isLoaded = name != null,
            bucketProjectCount = projectCount,
            isGenerating = generation.first,
            generationFailure = generation.second,
            rangePreset = selection.preset,
            customStart = selection.customStart,
            customEnd = selection.customEnd,
            isRangeInvalid = selection.isInvalid,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        ClerkMultiProjectStatementShareUiState(bucket = bucket),
    )

    private val _shareRequests = MutableSharedFlow<ShareFileRequest>(extraBufferCapacity = 1)
    val shareRequests: SharedFlow<ShareFileRequest> = _shareRequests

    fun onEvent(event: ClerkMultiProjectStatementShareEvent) {
        when (event) {
            is ClerkMultiProjectStatementShareEvent.RangePresetChanged -> onRangePresetChanged(event.preset)
            is ClerkMultiProjectStatementShareEvent.CustomStartChanged -> _customStart.value = event.date
            is ClerkMultiProjectStatementShareEvent.CustomEndChanged -> _customEnd.value = event.date
            ClerkMultiProjectStatementShareEvent.DismissGenerationFailure -> _generationFailure.value = false
            ClerkMultiProjectStatementShareEvent.ShareAsText -> shareAsText()
            ClerkMultiProjectStatementShareEvent.ShareAsPdf -> shareAsPdf()
        }
    }

    /** See [CompanyReportShareViewModel.onRangePresetChanged]'s KDoc — same display/apply agreement. */
    private fun onRangePresetChanged(preset: ReportRangePreset) {
        _rangePreset.value = preset
        if (preset == ReportRangePreset.CUSTOM) {
            if (_customStart.value == null) _customStart.value = today
            if (_customEnd.value == null) _customEnd.value = today
        }
    }

    private fun shareAsText() {
        val statement = shareableStatement() ?: return
        generate {
            val content = TextReportRenderer.renderClerkMultiProjectStatement(statement, strings)
            val fileName = ReportFileNames.clerkBucketStatementFileName(statement.clerkName, bucket, "txt")
            ShareFileRequest(fileWriter.writeText(fileName, content), "text/plain")
        }
    }

    private fun shareAsPdf() {
        val statement = shareableStatement() ?: return
        generate {
            val lines = ReportLines.forClerkMultiProjectStatement(statement, strings)
            val fileName = ReportFileNames.clerkBucketStatementFileName(statement.clerkName, bucket, "pdf")
            ShareFileRequest(fileWriter.writePdf(fileName, lines, strings.pageLabelTemplate), "application/pdf")
        }
    }

    /**
     * The statement to share, or null when there is nothing to share (still loading, invalid
     * range, empty bucket) or a file is already being written — the same re-entrancy guard the
     * other share ViewModels use, plus the v1.2 empty-bucket rule (nothing to share).
     */
    private fun shareableStatement(): ClerkMultiProjectStatement? {
        if (_isGenerating.value) return null
        return statementState.value?.takeIf { it.sections.isNotEmpty() }
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
            clerkId: String,
            bucket: ClerkProjectBucket,
            clerkDao: ClerkDao,
            projectDao: ProjectDao,
            companyDao: CompanyDao,
            attendanceEntryDao: AttendanceEntryDao,
            extraPayLineDao: ExtraPayLineDao,
            paymentDao: PaymentDao,
            fileWriter: ReportFileWriter,
            strings: ReportStrings,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ClerkMultiProjectStatementShareViewModel(
                    clerkId,
                    bucket,
                    clerkDao,
                    projectDao,
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
