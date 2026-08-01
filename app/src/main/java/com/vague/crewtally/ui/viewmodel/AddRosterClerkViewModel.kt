package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.data.local.ClerkDao
import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.data.local.RosterEntryDao
import com.vague.crewtally.data.local.RosterEntryEntity
import com.vague.crewtally.util.Money
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The two steps of adding an existing project's roster a new clerk: pick, then set a rate. */
enum class AddRosterClerkStep { PICK, RATE }

data class AddRosterClerkUiState(
    val step: AddRosterClerkStep = AddRosterClerkStep.PICK,
    val searchQuery: String = "",
    val selectedClerkId: String? = null,
    val selectedClerkName: String = "",
    val rateInput: String = "",
    val rateError: Boolean = false,
    val isSaving: Boolean = false,
    val saveComplete: Boolean = false,
)

sealed interface AddRosterClerkEvent {
    data class SearchChanged(val value: String) : AddRosterClerkEvent
    data class ClerkPicked(val clerkId: String, val clerkName: String) : AddRosterClerkEvent
    data class RateChanged(val value: String) : AddRosterClerkEvent
    data object StepBack : AddRosterClerkEvent
    data object Save : AddRosterClerkEvent
}

/**
 * Adds a clerk to a project's roster. The pick list only shows active clerks NOT already on
 * the project's active roster — a clerk removed earlier still shows up here, and picking them
 * again reuses their old (soft-removed) row rather than violating the (projectId, clerkId)
 * unique index with a second insert.
 */
class AddRosterClerkViewModel(
    private val projectId: String,
    clerkDao: ClerkDao,
    private val rosterEntryDao: RosterEntryDao,
) : ViewModel() {

    private val _state = MutableStateFlow(AddRosterClerkUiState())
    val state: StateFlow<AddRosterClerkUiState> = _state.asStateFlow()

    private val activeRosterClerkIds: StateFlow<Set<String>> =
        rosterEntryDao.observeActiveRosterForProject(projectId)
            .map { rows -> rows.map { it.entry.clerkId }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    private val allActiveClerks: StateFlow<List<ClerkEntity>> = clerkDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val availableClerks: StateFlow<List<ClerkEntity>> =
        combine(allActiveClerks, activeRosterClerkIds, _state) { clerks, rosterIds, form ->
            val query = form.searchQuery.trim()
            clerks.filter { it.id !in rosterIds && (query.isEmpty() || it.name.contains(query, ignoreCase = true)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun onEvent(event: AddRosterClerkEvent) {
        when (event) {
            is AddRosterClerkEvent.SearchChanged -> _state.update { it.copy(searchQuery = event.value) }
            is AddRosterClerkEvent.ClerkPicked -> onClerkPicked(event.clerkId, event.clerkName)
            is AddRosterClerkEvent.RateChanged -> _state.update { it.copy(rateInput = event.value, rateError = false) }
            AddRosterClerkEvent.StepBack -> _state.update { it.copy(step = AddRosterClerkStep.PICK) }
            AddRosterClerkEvent.Save -> onSave()
        }
    }

    private fun onClerkPicked(clerkId: String, clerkName: String) {
        viewModelScope.launch {
            val suggested = rosterEntryDao.getMostRecentRateForClerk(clerkId)?.let(Money::formatForInput).orEmpty()
            _state.update {
                it.copy(
                    step = AddRosterClerkStep.RATE,
                    selectedClerkId = clerkId,
                    selectedClerkName = clerkName,
                    rateInput = suggested,
                )
            }
        }
    }

    private fun onSave() {
        val current = _state.value
        val clerkId = current.selectedClerkId ?: return
        val rate = Money.parseToMinorUnits(current.rateInput)
        if (rate == null || rate <= 0L) {
            _state.update { it.copy(rateError = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val existing = rosterEntryDao.getForPair(projectId, clerkId)
            val entry = (
                existing ?: RosterEntryEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    clerkId = clerkId,
                    dailyRate = rate,
                )
                ).copy(dailyRate = rate, removedAt = null)
            rosterEntryDao.upsert(entry)
            _state.update { it.copy(isSaving = false, saveComplete = true) }
        }
    }

    companion object {
        fun factory(
            projectId: String,
            clerkDao: ClerkDao,
            rosterEntryDao: RosterEntryDao,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AddRosterClerkViewModel(projectId, clerkDao, rosterEntryDao) }
        }
    }
}
