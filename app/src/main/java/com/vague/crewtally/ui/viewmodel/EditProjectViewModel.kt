package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.data.local.CompanyDao
import com.vague.crewtally.data.local.CompanyEntity
import com.vague.crewtally.data.local.ProjectDao
import com.vague.crewtally.util.CurrencyCodes
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Same fields, labels, and validation as create-project step 1 (LOCKED Phase 2 decision). */
data class EditProjectUiState(
    val isLoaded: Boolean = false,
    val name: String = "",
    val nameError: Boolean = false,
    val selectedCompanyId: String? = null,
    val companyError: Boolean = false,
    val currency: String = "",
    val currencyError: Boolean = false,
    val location: String = "",
    val notes: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val isSaving: Boolean = false,
    val saveComplete: Boolean = false,
)

sealed interface EditProjectEvent {
    data class NameChanged(val value: String) : EditProjectEvent
    data class CompanySelected(val companyId: String) : EditProjectEvent
    data class CurrencyChanged(val value: String) : EditProjectEvent
    data class LocationChanged(val value: String) : EditProjectEvent
    data class NotesChanged(val value: String) : EditProjectEvent
    data class StartDateChanged(val value: LocalDate) : EditProjectEvent
    data object Save : EditProjectEvent
}

/** Edits an existing project's details (name, company, currency, location, notes, start date). */
class EditProjectViewModel(
    private val projectId: String,
    private val projectDao: ProjectDao,
    companyDao: CompanyDao,
) : ViewModel() {

    private val _state = MutableStateFlow(EditProjectUiState())
    val state: StateFlow<EditProjectUiState> = _state.asStateFlow()

    val companies: StateFlow<List<CompanyEntity>> = companyDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    init {
        viewModelScope.launch {
            val project = projectDao.getById(projectId) ?: return@launch
            _state.update {
                it.copy(
                    isLoaded = true,
                    name = project.name,
                    selectedCompanyId = project.companyId,
                    currency = project.currency,
                    location = project.location,
                    notes = project.notes,
                    startDate = project.startDate,
                )
            }
        }
    }

    fun onEvent(event: EditProjectEvent) {
        when (event) {
            is EditProjectEvent.NameChanged -> _state.update { it.copy(name = event.value, nameError = false) }
            is EditProjectEvent.CompanySelected ->
                _state.update { it.copy(selectedCompanyId = event.companyId, companyError = false) }
            is EditProjectEvent.CurrencyChanged ->
                _state.update { it.copy(currency = event.value, currencyError = false) }
            is EditProjectEvent.LocationChanged -> _state.update { it.copy(location = event.value) }
            is EditProjectEvent.NotesChanged -> _state.update { it.copy(notes = event.value) }
            is EditProjectEvent.StartDateChanged -> _state.update { it.copy(startDate = event.value) }
            EditProjectEvent.Save -> onSave()
        }
    }

    private fun onSave() {
        // The Compose disabled-state on the Save button lags a fast double tap by a frame;
        // this guard is the actual protection against firing the save twice.
        if (_state.value.isSaving) return
        val current = _state.value
        val nameError = current.name.isBlank()
        val companyError = current.selectedCompanyId == null
        // The curated dropdown values are always valid; this only gates free-text entry
        // (e.g. "US" or "dollars") that would otherwise reach save as a bogus currency code.
        val currencyError = !CurrencyCodes.isValidCode(current.currency.trim().uppercase())
        if (nameError || companyError || currencyError) {
            _state.update { it.copy(nameError = nameError, companyError = companyError, currencyError = currencyError) }
            return
        }
        val companyId = current.selectedCompanyId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val base = projectDao.getById(projectId)
            if (base == null) {
                // The project vanished mid-save (e.g. deleted from another entry point) —
                // reset isSaving so the button doesn't stay stuck disabled forever.
                _state.update { it.copy(isSaving = false) }
                return@launch
            }
            val updated = base.copy(
                name = current.name.trim(),
                companyId = companyId,
                currency = current.currency.trim().uppercase(),
                location = current.location.trim(),
                notes = current.notes.trim(),
                startDate = current.startDate,
            )
            projectDao.upsert(updated)
            _state.update { it.copy(isSaving = false, saveComplete = true) }
        }
    }

    companion object {
        fun factory(
            projectId: String,
            projectDao: ProjectDao,
            companyDao: CompanyDao,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { EditProjectViewModel(projectId, projectDao, companyDao) }
        }
    }
}
