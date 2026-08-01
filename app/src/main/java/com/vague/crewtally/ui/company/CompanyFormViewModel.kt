package com.vague.crewtally.ui.company

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.data.local.CompanyDao
import com.vague.crewtally.data.local.CompanyEntity
import com.vague.crewtally.data.local.ProjectDao
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompanyFormUiState(
    val companyId: String? = null,
    val name: String = "",
    val contactPerson: String = "",
    val phone: String = "",
    val notes: String = "",
    val isArchived: Boolean = false,
    /** True once a company with zero projects was confirmed loaded — gates whether Delete
     *  is offered at all (Phase 1 LOCKED: never a cascade delete). */
    val canDelete: Boolean = false,
    val isLoading: Boolean = true,
    /** Set after Save/Archive/Unarchive/Delete complete; the screen pops back on seeing it. */
    val isDone: Boolean = false,
) {
    val isEditing: Boolean get() = companyId != null
    val isNameValid: Boolean get() = name.isNotBlank()
    val canSave: Boolean get() = isNameValid
}

sealed interface CompanyFormEvent {
    data class NameChanged(val value: String) : CompanyFormEvent
    data class ContactPersonChanged(val value: String) : CompanyFormEvent
    data class PhoneChanged(val value: String) : CompanyFormEvent
    data class NotesChanged(val value: String) : CompanyFormEvent
    data object Save : CompanyFormEvent
    data object Archive : CompanyFormEvent
    data object Unarchive : CompanyFormEvent
    data object Delete : CompanyFormEvent
}

/**
 * Drives the company add/edit form under More. Mirrors [com.vague.crewtally.ui.clerk.ClerkFormViewModel]'s
 * shape exactly — same field-change/save/archive/delete event set, same "load once, gate
 * delete on a history count" flow — because Phase 1 deliberately gives Clerks and Companies
 * the identical list+form pattern; only the entity and its reference table (projects,
 * instead of roster/attendance/payments) differ.
 */
class CompanyFormViewModel(
    private val companyDao: CompanyDao,
    private val projectDao: ProjectDao,
    companyId: String?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompanyFormUiState(companyId = companyId, isLoading = companyId != null))
    val uiState: StateFlow<CompanyFormUiState> = _uiState.asStateFlow()

    init {
        if (companyId != null) {
            viewModelScope.launch {
                val company = companyDao.getById(companyId)
                val hasHistory = company != null && hasHistory(companyId)
                _uiState.update {
                    it.copy(
                        name = company?.name.orEmpty(),
                        contactPerson = company?.contactPerson.orEmpty(),
                        phone = company?.phone.orEmpty(),
                        notes = company?.notes.orEmpty(),
                        isArchived = company?.archived == true,
                        canDelete = company != null && !hasHistory,
                        isLoading = false,
                    )
                }
            }
        }
    }

    private suspend fun hasHistory(id: String): Boolean = projectDao.countByCompany(id) > 0

    fun onEvent(event: CompanyFormEvent) {
        when (event) {
            is CompanyFormEvent.NameChanged -> _uiState.update { it.copy(name = event.value) }
            is CompanyFormEvent.ContactPersonChanged -> _uiState.update { it.copy(contactPerson = event.value) }
            is CompanyFormEvent.PhoneChanged -> _uiState.update { it.copy(phone = event.value) }
            is CompanyFormEvent.NotesChanged -> _uiState.update { it.copy(notes = event.value) }
            CompanyFormEvent.Save -> save()
            CompanyFormEvent.Archive -> setArchived(true)
            CompanyFormEvent.Unarchive -> setArchived(false)
            CompanyFormEvent.Delete -> delete()
        }
    }

    private fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            companyDao.upsert(state.toEntity(id = state.companyId ?: UUID.randomUUID().toString()))
            _uiState.update { it.copy(isDone = true) }
        }
    }

    private fun setArchived(archived: Boolean) {
        val state = _uiState.value
        val id = state.companyId ?: return
        viewModelScope.launch {
            companyDao.upsert(state.copy(isArchived = archived).toEntity(id = id))
            _uiState.update { it.copy(isArchived = archived, isDone = true) }
        }
    }

    private fun delete() {
        val state = _uiState.value
        val id = state.companyId ?: return
        if (!state.canDelete) return
        viewModelScope.launch {
            // Projects may have been added after the form loaded; re-check right before
            // deleting rather than trusting the flag computed at load time.
            if (hasHistory(id)) {
                _uiState.update { it.copy(canDelete = false) }
                return@launch
            }
            try {
                companyDao.delete(state.toEntity(id = id))
                _uiState.update { it.copy(isDone = true) }
            } catch (e: SQLiteConstraintException) {
                // A foreign-key reference appeared between the recheck above and the
                // delete itself; treat it the same as a history recheck failure.
                _uiState.update { it.copy(canDelete = false) }
            }
        }
    }

    companion object {
        fun factory(
            companyDao: CompanyDao,
            projectDao: ProjectDao,
            companyId: String?,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { CompanyFormViewModel(companyDao, projectDao, companyId) }
        }
    }
}

private fun CompanyFormUiState.toEntity(id: String) = CompanyEntity(
    id = id,
    name = name.trim(),
    contactPerson = contactPerson.trim(),
    phone = phone.trim(),
    notes = notes.trim(),
    archived = isArchived,
)
