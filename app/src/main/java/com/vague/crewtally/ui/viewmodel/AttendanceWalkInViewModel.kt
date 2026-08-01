package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.data.local.AttendanceEntryDao
import com.vague.crewtally.data.local.AttendanceWriter
import com.vague.crewtally.data.local.ClerkDao
import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.data.local.ProjectRosterWriter
import com.vague.crewtally.data.local.RosterEntryDao
import com.vague.crewtally.util.Money
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Adding a walk-in is the same two steps as adding a roster clerk: pick, then set a rate. */
enum class AttendanceWalkInStep { PICK, RATE }

data class AttendanceWalkInUiState(
    val step: AttendanceWalkInStep = AttendanceWalkInStep.PICK,
    val searchQuery: String = "",
    val selectedClerkId: String? = null,
    val selectedClerkName: String = "",
    val rateInput: String = "",
    val rateError: Boolean = false,
    val alsoAddToRoster: Boolean = false,
    val isSaving: Boolean = false,
    val saveComplete: Boolean = false,
)

sealed interface AttendanceWalkInEvent {
    data class SearchChanged(val value: String) : AttendanceWalkInEvent
    data class ClerkPicked(val clerkId: String, val clerkName: String) : AttendanceWalkInEvent
    data class RateChanged(val value: String) : AttendanceWalkInEvent
    data class AlsoAddToRosterChanged(val value: Boolean) : AttendanceWalkInEvent
    data object StepBack : AttendanceWalkInEvent
    data object Save : AttendanceWalkInEvent
}

/**
 * Adds a clerk to a single attendance day (a walk-in). The pick list shows active clerks NOT
 * already on this day — neither on the active roster nor already carrying an attendance row for
 * the date. Save always writes a present=true attendance row with the entered rate as its
 * snapshot; if "Also add to project roster" is on it additionally reactivates/creates the
 * roster row via [ProjectRosterWriter.upsertRosterEntryForPair] (default OFF = day-only).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceWalkInViewModel(
    private val projectId: String,
    private val date: LocalDate,
    clerkDao: ClerkDao,
    private val rosterEntryDao: RosterEntryDao,
    attendanceEntryDao: AttendanceEntryDao,
    private val attendanceWriter: AttendanceWriter,
    private val rosterWriter: ProjectRosterWriter,
) : ViewModel() {

    private val _state = MutableStateFlow(AttendanceWalkInUiState())
    val state: StateFlow<AttendanceWalkInUiState> = _state.asStateFlow()

    private val activeRosterClerkIds: StateFlow<Set<String>> =
        rosterEntryDao.observeActiveRosterForProject(projectId)
            .map { rows -> rows.map { it.entry.clerkId }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    private val dayClerkIds: StateFlow<Set<String>> =
        attendanceEntryDao.observeDayWithNames(projectId, date)
            .map { rows -> rows.map { it.entry.clerkId }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    private val allActiveClerks: StateFlow<List<ClerkEntity>> = clerkDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val availableClerks: StateFlow<List<ClerkEntity>> =
        combine(allActiveClerks, activeRosterClerkIds, dayClerkIds, _state) { clerks, rosterIds, dayIds, form ->
            val query = form.searchQuery.trim()
            clerks.filter {
                it.id !in rosterIds &&
                    it.id !in dayIds &&
                    (query.isEmpty() || it.name.contains(query, ignoreCase = true))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun onEvent(event: AttendanceWalkInEvent) {
        when (event) {
            is AttendanceWalkInEvent.SearchChanged -> _state.update { it.copy(searchQuery = event.value) }
            is AttendanceWalkInEvent.ClerkPicked -> onClerkPicked(event.clerkId, event.clerkName)
            is AttendanceWalkInEvent.RateChanged -> _state.update { it.copy(rateInput = event.value, rateError = false) }
            is AttendanceWalkInEvent.AlsoAddToRosterChanged -> _state.update { it.copy(alsoAddToRoster = event.value) }
            AttendanceWalkInEvent.StepBack -> _state.update { it.copy(step = AttendanceWalkInStep.PICK) }
            AttendanceWalkInEvent.Save -> onSave()
        }
    }

    private fun onClerkPicked(clerkId: String, clerkName: String) {
        viewModelScope.launch {
            val suggested = rosterEntryDao.getMostRecentRateForClerk(clerkId)?.let(Money::formatForInput).orEmpty()
            _state.update {
                it.copy(
                    step = AttendanceWalkInStep.RATE,
                    selectedClerkId = clerkId,
                    selectedClerkName = clerkName,
                    rateInput = suggested,
                )
            }
        }
    }

    private fun onSave() {
        if (_state.value.isSaving) return
        val current = _state.value
        val clerkId = current.selectedClerkId ?: return
        val rate = Money.parseToMinorUnits(current.rateInput)
        if (rate == null || rate <= 0L) {
            _state.update { it.copy(rateError = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            // Day-only walk-in: a present=true attendance row with the entered rate snapshotted.
            attendanceWriter.setAttendance(projectId, clerkId, date, present = true, rateSnapshot = rate)
            if (current.alsoAddToRoster) {
                // Reuse the roster writer's atomic id-reuse path rather than reinventing it.
                rosterWriter.upsertRosterEntryForPair(projectId, clerkId, rate)
            }
            _state.update { it.copy(isSaving = false, saveComplete = true) }
        }
    }

    companion object {
        fun factory(
            projectId: String,
            date: LocalDate,
            clerkDao: ClerkDao,
            rosterEntryDao: RosterEntryDao,
            attendanceEntryDao: AttendanceEntryDao,
            attendanceWriter: AttendanceWriter,
            rosterWriter: ProjectRosterWriter,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AttendanceWalkInViewModel(
                    projectId,
                    date,
                    clerkDao,
                    rosterEntryDao,
                    attendanceEntryDao,
                    attendanceWriter,
                    rosterWriter,
                )
            }
        }
    }
}
