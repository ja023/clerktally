package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.data.local.AttendanceEntryDao
import com.vague.crewtally.data.local.AttendanceWriter
import com.vague.crewtally.data.local.ExtraPayLineDao
import com.vague.crewtally.data.local.ExtraPayLineEntity
import com.vague.crewtally.util.Money
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The preset extra-pay labels, plus a free-text option. Order is the chip order in the UI. */
enum class ExtraPreset { LUNCH, TRANSPORT, BONUS, CUSTOM }

data class AttendanceExtrasUiState(
    val preset: ExtraPreset = ExtraPreset.LUNCH,
    val customLabel: String = "",
    val amountInput: String = "",
    val isDeduction: Boolean = false,
    val amountError: Boolean = false,
    val customLabelError: Boolean = false,
    val isSaving: Boolean = false,
)

sealed interface AttendanceExtrasEvent {
    data class PresetChanged(val preset: ExtraPreset) : AttendanceExtrasEvent
    data class CustomLabelChanged(val value: String) : AttendanceExtrasEvent
    data class AmountChanged(val value: String) : AttendanceExtrasEvent
    data class DeductionChanged(val value: Boolean) : AttendanceExtrasEvent
    data object AddLine : AttendanceExtrasEvent
    data class DeleteLine(val line: ExtraPayLineEntity) : AttendanceExtrasEvent
}

/**
 * Backs the per clerk-day extras editor. Extras attach to ANY status: opening the editor for
 * an Unmarked or Absent clerk lazily creates a present=false carrier attendance row (via
 * [AttendanceWriter.ensureAttendanceEntry]) the first time a line is added, so the extra has
 * something to hang on. [rateSnapshot] is the rate that carrier is created with.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceExtrasViewModel(
    private val projectId: String,
    private val clerkId: String,
    private val date: LocalDate,
    private val rateSnapshot: Long,
    attendanceEntryDao: AttendanceEntryDao,
    private val extraPayLineDao: ExtraPayLineDao,
    private val attendanceWriter: AttendanceWriter,
) : ViewModel() {

    private val _state = MutableStateFlow(AttendanceExtrasUiState())
    val state: StateFlow<AttendanceExtrasUiState> = _state.asStateFlow()

    private val addMutex = Mutex()

    /** The current lines for this clerk-day: re-derived whenever the carrier entry appears/changes. */
    val lines: StateFlow<List<ExtraPayLineEntity>> =
        attendanceEntryDao.observeByProjectClerkDate(projectId, clerkId, date)
            .flatMapLatest { entry ->
                if (entry == null) {
                    flowOf(emptyList())
                } else {
                    extraPayLineDao.observeForAttendance(entry.id)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** Running signed total of all lines, in minor units. */
    val total: StateFlow<Long> = lines
        .map { list -> list.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0L)

    fun onEvent(event: AttendanceExtrasEvent) {
        when (event) {
            is AttendanceExtrasEvent.PresetChanged ->
                _state.update { it.copy(preset = event.preset, customLabelError = false) }
            is AttendanceExtrasEvent.CustomLabelChanged ->
                _state.update { it.copy(customLabel = event.value, customLabelError = false) }
            is AttendanceExtrasEvent.AmountChanged ->
                _state.update { it.copy(amountInput = event.value, amountError = false) }
            is AttendanceExtrasEvent.DeductionChanged ->
                _state.update { it.copy(isDeduction = event.value) }
            AttendanceExtrasEvent.AddLine -> onAddLine()
            is AttendanceExtrasEvent.DeleteLine -> onDeleteLine(event.line)
        }
    }

    private fun onAddLine() {
        if (_state.value.isSaving) return
        val current = _state.value
        val magnitude = Money.parseToMinorUnits(current.amountInput)
        val labelIsCustom = current.preset == ExtraPreset.CUSTOM
        val customBlank = labelIsCustom && current.customLabel.isBlank()
        if (magnitude == null || magnitude <= 0L || customBlank) {
            _state.update {
                it.copy(amountError = magnitude == null || magnitude <= 0L, customLabelError = customBlank)
            }
            return
        }
        val signedAmount = if (current.isDeduction) -magnitude else magnitude
        viewModelScope.launch {
            addMutex.withLock {
                _state.update { it.copy(isSaving = true) }
                // Ensure the carrier row exists (present=false if the clerk was Unmarked/Absent)
                // and attach the line to it — atomic id reuse guards the unique index.
                val entryId = attendanceWriter.ensureAttendanceEntry(projectId, clerkId, date, rateSnapshot)
                extraPayLineDao.upsert(
                    ExtraPayLineEntity(
                        id = UUID.randomUUID().toString(),
                        attendanceEntryId = entryId,
                        label = labelFor(current),
                        amount = signedAmount,
                    ),
                )
                _state.update {
                    it.copy(
                        isSaving = false,
                        amountInput = "",
                        customLabel = "",
                        isDeduction = false,
                        preset = ExtraPreset.LUNCH,
                    )
                }
            }
        }
    }

    private fun onDeleteLine(line: ExtraPayLineEntity) {
        viewModelScope.launch { extraPayLineDao.delete(line) }
    }

    /** Preset label keys resolved to display text by the screen; CUSTOM uses the typed label. */
    private fun labelFor(state: AttendanceExtrasUiState): String = when (state.preset) {
        ExtraPreset.LUNCH -> PRESET_LABEL_LUNCH
        ExtraPreset.TRANSPORT -> PRESET_LABEL_TRANSPORT
        ExtraPreset.BONUS -> PRESET_LABEL_BONUS
        ExtraPreset.CUSTOM -> state.customLabel.trim()
    }

    companion object {
        // Preset labels are stored verbatim on the line (they become report/statement text
        // later), so they are stable keys, not localized at read time.
        const val PRESET_LABEL_LUNCH = "Lunch"
        const val PRESET_LABEL_TRANSPORT = "Transport"
        const val PRESET_LABEL_BONUS = "Bonus"

        fun factory(
            projectId: String,
            clerkId: String,
            date: LocalDate,
            rateSnapshot: Long,
            attendanceEntryDao: AttendanceEntryDao,
            extraPayLineDao: ExtraPayLineDao,
            attendanceWriter: AttendanceWriter,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AttendanceExtrasViewModel(
                    projectId,
                    clerkId,
                    date,
                    rateSnapshot,
                    attendanceEntryDao,
                    extraPayLineDao,
                    attendanceWriter,
                )
            }
        }
    }
}
