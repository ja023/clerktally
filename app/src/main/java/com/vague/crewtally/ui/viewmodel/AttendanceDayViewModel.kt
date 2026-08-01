package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.data.local.AttendanceEntryDao
import com.vague.crewtally.data.local.AttendanceWriter
import com.vague.crewtally.data.local.ClearAttendanceResult
import com.vague.crewtally.data.local.ClerkRate
import com.vague.crewtally.data.local.ExtraPayLineDao
import com.vague.crewtally.data.local.ProjectDao
import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.data.local.ProjectStatus
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
 * Every write goes through the writer's atomic look-up-id-then-write transaction. Ordering
 * across rapid taps — and across every OTHER attendance screen writing through the same shared
 * [AttendanceWriter] instance — is [AttendanceWriter]'s own guarantee now (its internal mutex),
 * not this ViewModel's; a per-ViewModel mutex could never serialize against a sibling extras or
 * walk-in screen writing concurrently. Each write handler captures the selected [_date] into a
 * local `val` synchronously, before launching its coroutine — [_date] is a mutable StateFlow the
 * user can change (prev/next/date-picker) while a write is still in flight, so reading it lazily
 * inside the launched block could apply a queued write to whatever day the user has since
 * navigated to instead of the day that was showing when the tap happened.
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

    /**
     * True when the selected date is after today (AMENDED 2026-08-02: same warn-don't-block
     * treatment as a backfill date, just in the other direction).
     */
    val isAfterToday: StateFlow<Boolean> = _date
        .map { day -> day.isAfter(LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    /**
     * True when the project is COMPLETED — the day screen still works on it (LOCKED #11: history
     * never locks), just with a soft non-blocking notice (AMENDED 2026-08-02).
     */
    val isProjectCompleted: StateFlow<Boolean> = project
        .map { current -> current?.status == ProjectStatus.COMPLETED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

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
        // Captured synchronously — before launch — so a date change while this write is
        // queued/in-flight can't retarget it onto whatever day the user has navigated to since.
        val day = _date.value
        if (row.state == tapped) {
            // Tapping the already-selected state clears the clerk back to Unmarked.
            requestUnmark(row)
        } else {
            val present = tapped == AttendanceState.PRESENT
            viewModelScope.launch {
                attendanceWriter.setAttendance(projectId, clerkId, day, present, row.rateMinorUnits)
            }
        }
    }

    /**
     * Extras cascade-delete with the row, so an unmark that would drop extras asks first. The
     * extras check now lives inside [AttendanceWriter.clearAttendance]'s own transaction (not a
     * client-side pre-check here) so a concurrently-added extra can never sneak in between a
     * check and a delete that used to be two separate, unlocked steps.
     */
    private fun requestUnmark(row: AttendanceRowUi) {
        row.attendanceEntryId ?: return
        val day = _date.value
        viewModelScope.launch {
            val result = attendanceWriter.clearAttendance(projectId, row.clerkId, day)
            if (result is ClearAttendanceResult.BlockedByExtras) {
                _pendingUnmark.value = row
            }
        }
    }

    private fun onConfirmUnmark() {
        val row = _pendingUnmark.value ?: return
        _pendingUnmark.value = null
        val day = _date.value
        viewModelScope.launch {
            attendanceWriter.clearAttendance(projectId, row.clerkId, day, force = true)
        }
    }

    private fun onMarkAllPresent() {
        val clerkRates = rows.value
            .filter { !it.isWalkIn }
            .map { ClerkRate(it.clerkId, it.rateMinorUnits) }
        if (clerkRates.isEmpty()) return
        val day = _date.value
        viewModelScope.launch {
            // The writer skips clerks that already have a row, so explicit Absent marks
            // (and anyone already Present) are left untouched.
            attendanceWriter.markAllPresent(projectId, day, clerkRates)
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
