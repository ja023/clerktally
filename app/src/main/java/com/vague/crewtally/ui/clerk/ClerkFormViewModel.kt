package com.vague.crewtally.ui.clerk

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.data.local.AttendanceEntryDao
import com.vague.crewtally.data.local.ClerkDao
import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.data.local.PaymentDao
import com.vague.crewtally.data.local.RosterEntryDao
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClerkFormUiState(
    val clerkId: String? = null,
    val name: String = "",
    val phone: String = "",
    val notes: String = "",
    val isArchived: Boolean = false,
    /** True once a clerk with no roster/attendance/payment history was confirmed loaded —
     *  gates whether Delete is offered at all (Phase 1 LOCKED: never a cascade delete). */
    val canDelete: Boolean = false,
    val isLoading: Boolean = true,
    /** Set after Save/Archive/Unarchive/Delete complete; the screen pops back on seeing it. */
    val isDone: Boolean = false,
) {
    val isEditing: Boolean get() = clerkId != null
    val isNameValid: Boolean get() = name.isNotBlank()
    val canSave: Boolean get() = isNameValid
}

sealed interface ClerkFormEvent {
    data class NameChanged(val value: String) : ClerkFormEvent
    data class PhoneChanged(val value: String) : ClerkFormEvent
    data class NotesChanged(val value: String) : ClerkFormEvent
    data object Save : ClerkFormEvent
    data object Archive : ClerkFormEvent
    data object Unarchive : ClerkFormEvent
    data object Delete : ClerkFormEvent
}

/**
 * Drives the clerk add/edit form. `clerkId == null` means "new clerk"; a non-null id loads
 * the existing record and unlocks the archive/unarchive/delete controls (Phase 1 LOCKED:
 * those live on the edit form, never a per-row overflow menu — see the Phase 1 report for
 * why that affordance was chosen).
 *
 * Delete eligibility is computed once on load from three independent history sources
 * (roster, attendance, payments) rather than a join, since Phase 1 has no reason to know
 * more than "zero vs. some" and each DAO already owns its own count query.
 */
class ClerkFormViewModel(
    private val clerkDao: ClerkDao,
    private val rosterEntryDao: RosterEntryDao,
    private val attendanceEntryDao: AttendanceEntryDao,
    private val paymentDao: PaymentDao,
    clerkId: String?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClerkFormUiState(clerkId = clerkId, isLoading = clerkId != null))
    val uiState: StateFlow<ClerkFormUiState> = _uiState.asStateFlow()

    init {
        if (clerkId != null) {
            viewModelScope.launch {
                val clerk = clerkDao.getById(clerkId)
                val hasHistory = clerk != null && hasHistory(clerkId)
                _uiState.update {
                    it.copy(
                        name = clerk?.name.orEmpty(),
                        phone = clerk?.phone.orEmpty(),
                        notes = clerk?.notes.orEmpty(),
                        isArchived = clerk?.active == false,
                        canDelete = clerk != null && !hasHistory,
                        isLoading = false,
                    )
                }
            }
        }
    }

    private suspend fun hasHistory(id: String): Boolean =
        rosterEntryDao.countByClerk(id) > 0 ||
            attendanceEntryDao.countByClerk(id) > 0 ||
            paymentDao.countByClerk(id) > 0

    fun onEvent(event: ClerkFormEvent) {
        when (event) {
            is ClerkFormEvent.NameChanged -> _uiState.update { it.copy(name = event.value) }
            is ClerkFormEvent.PhoneChanged -> _uiState.update { it.copy(phone = event.value) }
            is ClerkFormEvent.NotesChanged -> _uiState.update { it.copy(notes = event.value) }
            ClerkFormEvent.Save -> save()
            ClerkFormEvent.Archive -> setArchived(true)
            ClerkFormEvent.Unarchive -> setArchived(false)
            ClerkFormEvent.Delete -> delete()
        }
    }

    private fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            clerkDao.upsert(state.toEntity(id = state.clerkId ?: UUID.randomUUID().toString()))
            _uiState.update { it.copy(isDone = true) }
        }
    }

    private fun setArchived(archived: Boolean) {
        val state = _uiState.value
        val id = state.clerkId ?: return
        viewModelScope.launch {
            clerkDao.upsert(state.copy(isArchived = archived).toEntity(id = id))
            _uiState.update { it.copy(isArchived = archived, isDone = true) }
        }
    }

    private fun delete() {
        val state = _uiState.value
        val id = state.clerkId ?: return
        if (!state.canDelete) return
        viewModelScope.launch {
            // History may have been added after the form loaded (e.g. rostered onto a
            // project from another screen); re-check right before deleting rather than
            // trusting the flag computed at load time.
            if (hasHistory(id)) {
                _uiState.update { it.copy(canDelete = false) }
                return@launch
            }
            try {
                clerkDao.delete(state.toEntity(id = id))
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
            clerkDao: ClerkDao,
            rosterEntryDao: RosterEntryDao,
            attendanceEntryDao: AttendanceEntryDao,
            paymentDao: PaymentDao,
            clerkId: String?,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ClerkFormViewModel(clerkDao, rosterEntryDao, attendanceEntryDao, paymentDao, clerkId) }
        }
    }
}

private fun ClerkFormUiState.toEntity(id: String) = ClerkEntity(
    id = id,
    name = name.trim(),
    phone = phone.trim(),
    notes = notes.trim(),
    active = !isArchived,
)
