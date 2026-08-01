package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.data.local.RosterEntryDao
import com.vague.crewtally.data.local.RosterEntryEntity
import com.vague.crewtally.util.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditRosterRateUiState(
    val rateInput: String = "",
    val rateError: Boolean = false,
    val isSaving: Boolean = false,
    val saveComplete: Boolean = false,
    val removeComplete: Boolean = false,
)

sealed interface EditRosterRateEvent {
    data class RateChanged(val value: String) : EditRosterRateEvent
    data object Save : EditRosterRateEvent
    data object Remove : EditRosterRateEvent
}

/**
 * Edits one clerk's rate on a project's roster, or removes them from it. Both actions
 * [Room.Upsert] onto the SAME [rosterEntryId] the screen was opened for, so this never needs
 * to look the row back up — editing a rate here only ever affects FUTURE attendance days;
 * past days already snapshotted their rate onto [com.vague.crewtally.data.local.AttendanceEntryEntity.rateSnapshot]
 * (LOCKED data model) and are untouched.
 */
class EditRosterRateViewModel(
    private val rosterEntryId: String,
    private val projectId: String,
    private val clerkId: String,
    val clerkName: String,
    private val initialRateMinorUnits: Long,
    private val rosterEntryDao: RosterEntryDao,
) : ViewModel() {

    private val _state = MutableStateFlow(EditRosterRateUiState(rateInput = Money.formatForInput(initialRateMinorUnits)))
    val state: StateFlow<EditRosterRateUiState> = _state.asStateFlow()

    fun onEvent(event: EditRosterRateEvent) {
        when (event) {
            is EditRosterRateEvent.RateChanged -> _state.update { it.copy(rateInput = event.value, rateError = false) }
            EditRosterRateEvent.Save -> onSave()
            EditRosterRateEvent.Remove -> onRemove()
        }
    }

    private fun onSave() {
        val rate = Money.parseToMinorUnits(_state.value.rateInput)
        if (rate == null || rate <= 0L) {
            _state.update { it.copy(rateError = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            rosterEntryDao.upsert(
                RosterEntryEntity(
                    id = rosterEntryId,
                    projectId = projectId,
                    clerkId = clerkId,
                    dailyRate = rate,
                    removedAt = null,
                ),
            )
            _state.update { it.copy(isSaving = false, saveComplete = true) }
        }
    }

    private fun onRemove() {
        viewModelScope.launch {
            rosterEntryDao.upsert(
                RosterEntryEntity(
                    id = rosterEntryId,
                    projectId = projectId,
                    clerkId = clerkId,
                    dailyRate = initialRateMinorUnits,
                    removedAt = System.currentTimeMillis(),
                ),
            )
            _state.update { it.copy(removeComplete = true) }
        }
    }

    companion object {
        fun factory(
            rosterEntryId: String,
            projectId: String,
            clerkId: String,
            clerkName: String,
            initialRateMinorUnits: Long,
            rosterEntryDao: RosterEntryDao,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EditRosterRateViewModel(
                    rosterEntryId,
                    projectId,
                    clerkId,
                    clerkName,
                    initialRateMinorUnits,
                    rosterEntryDao,
                )
            }
        }
    }
}
