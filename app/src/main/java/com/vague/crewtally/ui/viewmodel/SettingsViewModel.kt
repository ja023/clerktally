package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.backup.BackupPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(val nudgeEnabled: Boolean = true, val versionName: String = "")

sealed interface SettingsEvent {
    data class NudgeToggled(val enabled: Boolean) : SettingsEvent
}

/**
 * Backs the Settings screen (LOCKED Phase 5): the one toggle (the 30-day backup nudge) plus the
 * read-only About info. Writes through immediately to [BackupPreferences] — there is no separate
 * Save step, matching the toggle-row pattern used everywhere else in the app (e.g. the archived
 * filter on the Clerks/Companies lists).
 */
class SettingsViewModel(
    private val backupPreferences: BackupPreferences,
    versionName: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(nudgeEnabled = backupPreferences.nudgeEnabled, versionName = versionName),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.NudgeToggled -> {
                backupPreferences.nudgeEnabled = event.enabled
                _uiState.update { it.copy(nudgeEnabled = event.enabled) }
            }
        }
    }

    companion object {
        fun factory(backupPreferences: BackupPreferences, versionName: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SettingsViewModel(backupPreferences, versionName) }
            }
    }
}
