package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.data.local.AttendanceEntryDao
import com.vague.crewtally.data.local.AttendanceWriter
import com.vague.crewtally.data.local.ClerkRate
import com.vague.crewtally.data.local.ExtraPayLineDao
import com.vague.crewtally.data.local.ProjectDao
import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.data.local.RosterEntryDao
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The three attendance states a clerk row can show. Unmarked = no attendance row exists. */
enum class AttendanceState { UNMARKED, PRESENT, ABSENT }

/**
 * One clerk's row on the attendance day screen.
 *
 * @param rateMinorUnits the rate shown, and the rate snapshotted when this clerk is marked:
 *   the current roster rate for a roster clerk, or the walk-in's own snapshot for a day-only
 *   walk-in (who has no roster rate to read).
 * @param attendanceEntryId the id of the backing row, or null while Unmarked.
 * @param isWalkIn true for a day-only walk-in (has an attendance row but no active roster row).
 */
data class AttendanceRowUi(
    val clerkId: String,
    val clerkName: String,
    val rateMinorUnits: Long,
    val state: AttendanceState,
    val extrasTotalMinorUnits: Long,
    val attendanceEntryId: String?,
    val isWalkIn: Boolean,
)

sealed interface AttendanceDayEvent {
    data object PrevDay : AttendanceDayEvent
    data object NextDay : AttendanceDayEvent
    data class DateSelected(val date: LocalDate) : AttendanceDayEvent
    data class PresentTapped(val clerkId: String) : AttendanceDayEvent
    data class AbsentTapped(val clerkId: String) : AttendanceDayEvent
    data object MarkAllPresent : AttendanceDayEvent
    data object ConfirmUnmark : AttendanceDayEvent
    data object DismissUnmark : AttendanceDayEvent
}

/**
 * Backs the attendance daily loop — the app's most-used surface. Rows are the project's active
 * roster clerks unioned with any day-only walk-ins already recorded for the selected date; each
 * carries a three-state Unmarked / Present / Absent status that auto-saves on every tap through
 * [AttendanceWriter] (no Save button).
 *
 * Every write goes through the writer's atomic look-up-id-then-write transaction, and a single
 * [writeMutex] serializes the fire-and-forget taps so a rapid Present -> Absent -> Present
 * sequence applies in order and settles as exactly one row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceDayViewModel(
    private val projectId: String,
    initialDate: LocalDate,
    projectDao: ProjectDao,
    private val rosterEntryDao: RosterEntryDao,
    private val attendanceEntryDao: AttendanceEntryDao,
    private val extraPayLineDao: ExtraPayLineDao,
    private val attendanceWriter: AttendanceWriter,
) : ViewModel() {

    private val _date = MutableStateFlow(initialDate)
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    /** The clerk whose unmark is awaiting confirmation because their row carries extras. */
    private val _pendingUnmark = MutableStateFlow<AttendanceRowUi?>(null)
    val pendingUnmark: StateFlow<AttendanceRowUi?> = _pendingUnmark.asStateFlow()

    private val writeMutex = Mutex()

    val project: StateFlow<ProjectEntity?> = projectDao.observeById(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val rows: StateFlow<List<AttendanceRowUi>> = _date
        .flatMapLatest { day ->
            combine(
                rosterEntryDao.observeActiveRosterForProject(projectId),
                attendanceEntryDao.observeDayWithNames(projectId, day),
                extraPayLineDao.observeDayExtraTotals(projectId, day),
            ) { roster, dayEntries, extraTotals ->
                val entryByClerk = dayEntries.associateBy { it.entry.clerkId }
                val extrasByEntryId = extraTotals.associate { it.attendanceEntryId to it.total }
                val rosterClerkIds = roster.map { it.entry.clerkId }.toSet()

                val rosterRows = roster.map { rosterRow ->
                    val entry = entryByClerk[rosterRow.entry.clerkId]
                    AttendanceRowUi(
                        clerkId = rosterRow.entry.clerkId,
                        clerkName = rosterRow.clerkName,
                        rateMinorUnits = rosterRow.entry.dailyRate,
                        state = stateOf(entry?.entry?.present),
                        extrasTotalMinorUnits = entry?.let { extrasByEntryId[it.entry.id] } ?: 0L,
                        attendanceEntryId = entry?.entry?.id,
                        isWalkIn = false,
                    )
                }
                val walkInRows = dayEntries
                    .filter { it.entry.clerkId !in rosterClerkIds }
                    .map { walkIn ->
                        AttendanceRowUi(
                            clerkId = walkIn.entry.clerkId,
                            clerkName = walkIn.clerkName,
                            rateMinorUnits = walkIn.entry.rateSnapshot,
                            state = stateOf(walkIn.entry.present),
                            extrasTotalMinorUnits = extrasByEntryId[walkIn.entry.id] ?: 0L,
                            attendanceEntryId = walkIn.entry.id,
                            isWalkIn = true,
                        )
                    }
                rosterRows + walkInRows
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** Clerks still showing Unmarked — drives the soft, non-blocking reminder line. */
    val unmarkedCount: StateFlow<Int> = rows
        .map { list -> list.count { it.state == AttendanceState.UNMARKED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    /** True when the selected date is before the project's start date (backfill warning, never blocks). */
    val isBeforeStartDate: StateFlow<Boolean> = combine(_date, project) { day, current ->
        current != null && day.isBefore(current.startDate)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    fun onEvent(event: AttendanceDayEvent) {
        when (event) {
            AttendanceDayEvent.PrevDay -> _date.value = _date.value.minusDays(1)
            AttendanceDayEvent.NextDay -> _date.value = _date.value.plusDays(1)
            is AttendanceDayEvent.DateSelected -> _date.value = event.date
            is AttendanceDayEvent.PresentTapped -> onStatusTapped(event.clerkId, AttendanceState.PRESENT)
            is AttendanceDayEvent.AbsentTapped -> onStatusTapped(event.clerkId, AttendanceState.ABSENT)
            AttendanceDayEvent.MarkAllPresent -> onMarkAllPresent()
            AttendanceDayEvent.ConfirmUnmark -> onConfirmUnmark()
            AttendanceDayEvent.DismissUnmark -> _pendingUnmark.value = null
        }
    }

    private fun onStatusTapped(clerkId: String, tapped: AttendanceState) {
        val row = rows.value.find { it.clerkId == clerkId } ?: return
        if (row.state == tapped) {
            // Tapping the already-selected state clears the clerk back to Unmarked.
            requestUnmark(row)
        } else {
            val present = tapped == AttendanceState.PRESENT
            viewModelScope.launch {
                writeMutex.withLock {
                    attendanceWriter.setAttendance(projectId, clerkId, _date.value, present, row.rateMinorUnits)
                }
            }
        }
    }

    /** Extras cascade-delete with the row, so an unmark that would drop extras asks first. */
    private fun requestUnmark(row: AttendanceRowUi) {
        val entryId = row.attendanceEntryId ?: return
        viewModelScope.launch {
            val hasExtras = extraPayLineDao.getForAttendance(entryId).isNotEmpty()
            if (hasExtras) {
                _pendingUnmark.value = row
            } else {
                writeMutex.withLock {
                    attendanceWriter.clearAttendance(projectId, row.clerkId, _date.value)
                }
            }
        }
    }

    private fun onConfirmUnmark() {
        val row = _pendingUnmark.value ?: return
        _pendingUnmark.value = null
        viewModelScope.launch {
            writeMutex.withLock {
                attendanceWriter.clearAttendance(projectId, row.clerkId, _date.value)
            }
        }
    }

    private fun onMarkAllPresent() {
        val clerkRates = rows.value
            .filter { !it.isWalkIn }
            .map { ClerkRate(it.clerkId, it.rateMinorUnits) }
        if (clerkRates.isEmpty()) return
        viewModelScope.launch {
            writeMutex.withLock {
                // The writer skips clerks that already have a row, so explicit Absent marks
                // (and anyone already Present) are left untouched.
                attendanceWriter.markAllPresent(projectId, _date.value, clerkRates)
            }
        }
    }

    private fun stateOf(present: Boolean?): AttendanceState = when (present) {
        null -> AttendanceState.UNMARKED
        true -> AttendanceState.PRESENT
        false -> AttendanceState.ABSENT
    }

    companion object {
        fun factory(
            projectId: String,
            initialDate: LocalDate,
            projectDao: ProjectDao,
            rosterEntryDao: RosterEntryDao,
            attendanceEntryDao: AttendanceEntryDao,
            extraPayLineDao: ExtraPayLineDao,
            attendanceWriter: AttendanceWriter,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AttendanceDayViewModel(
                    projectId,
                    initialDate,
                    projectDao,
                    rosterEntryDao,
                    attendanceEntryDao,
                    extraPayLineDao,
                    attendanceWriter,
                )
            }
        }
    }
}
