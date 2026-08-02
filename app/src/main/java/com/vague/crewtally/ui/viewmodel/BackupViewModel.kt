package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.backup.BackupCounts
import com.vague.crewtally.backup.BackupExporter
import com.vague.crewtally.backup.BackupPayload
import com.vague.crewtally.backup.BackupPreferences
import com.vague.crewtally.backup.BackupSerializer
import com.vague.crewtally.backup.BackupValidationError
import com.vague.crewtally.backup.BackupValidationResult
import com.vague.crewtally.backup.BackupValidator
import com.vague.crewtally.backup.RestoreWriter
import com.vague.crewtally.report.ReportFileNames
import com.vague.crewtally.report.ReportFileWriter
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A validated payload sitting behind the destructive restore confirm dialog, awaiting the user's decision. */
data class PendingRestore(val payload: BackupPayload, val counts: BackupCounts)

/**
 * Why a restore did not complete — validation rejected the file, the write itself failed, or the
 * picked file never made it to validation at all ([FileTooLarge]/[FileUnreadable], raised by the
 * screen before [BackupValidator] ever sees any content).
 */
sealed interface RestoreFailure {
    data class Validation(val error: BackupValidationError) : RestoreFailure
    data object WriteFailed : RestoreFailure

    /** The picked file is bigger than any real CrewTally backup could plausibly be. */
    data object FileTooLarge : RestoreFailure

    /** The platform could not open/read the picked file at all (permission revoked, provider gone). */
    data object FileUnreadable : RestoreFailure
}

data class BackupUiState(
    val isExporting: Boolean = false,
    val isRestoring: Boolean = false,
    val isValidatingRestore: Boolean = false,
    val pendingRestore: PendingRestore? = null,
    val restoreFailure: RestoreFailure? = null,
    val restoreSuccess: BackupCounts? = null,
)

sealed interface BackupEvent {
    data object ExportRequested : BackupEvent

    /** [content] is the already-read text of the file the user picked via SAF — read by the screen, not this ViewModel. */
    data class RestoreFilePicked(val content: String) : BackupEvent

    /** The screen rejected the picked file before it ever reached [BackupValidator] (too large, unreadable). */
    data class RestoreFileRejected(val failure: RestoreFailure) : BackupEvent
    data object ConfirmRestore : BackupEvent
    data object DismissRestoreConfirm : BackupEvent
    data object DismissRestoreFailure : BackupEvent
    data object DismissRestoreSuccess : BackupEvent
}

/**
 * Backs the Backup screen (LOCKED Phase 5): export builds the whole-database JSON off the main
 * thread and hands it to the share sheet, recording [BackupPreferences.lastExportEpochMillis] so
 * the More-screen nudge resets; restore validates fully (never touching the database on a bad
 * file — see [BackupValidator]) before showing a destructive confirm, then runs the actual write
 * through [RestoreWriter] in one transaction. [isExporting]/[isRestoring] double as re-entrancy
 * guards, matching [ClerkStatementShareViewModel]'s pattern; [isValidatingRestore] fills the gap
 * between picking a file and the confirm dialog appearing, so that gap never reads as dead
 * silence to a screen reader.
 */
class BackupViewModel(
    private val backupExporter: BackupExporter,
    private val fileWriter: ReportFileWriter,
    private val restoreWriter: RestoreWriter,
    private val backupPreferences: BackupPreferences,
    private val appVersionName: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _shareRequests = MutableSharedFlow<ShareFileRequest>(extraBufferCapacity = 1)
    val shareRequests: SharedFlow<ShareFileRequest> = _shareRequests

    fun onEvent(event: BackupEvent) {
        when (event) {
            BackupEvent.ExportRequested -> onExport()
            is BackupEvent.RestoreFilePicked -> onRestoreFilePicked(event.content)
            is BackupEvent.RestoreFileRejected -> _uiState.update { it.copy(restoreFailure = event.failure) }
            BackupEvent.ConfirmRestore -> onConfirmRestore()
            BackupEvent.DismissRestoreConfirm -> _uiState.update { it.copy(pendingRestore = null) }
            BackupEvent.DismissRestoreFailure -> _uiState.update { it.copy(restoreFailure = null) }
            BackupEvent.DismissRestoreSuccess -> _uiState.update { it.copy(restoreSuccess = null) }
        }
    }

    private fun onExport() {
        if (_uiState.value.isExporting) return // re-entrancy guard
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            val exportedAt = clock()
            val request = withContext(Dispatchers.IO) {
                val payload = backupExporter.export(appVersionName, exportedAt)
                val json = BackupSerializer.encode(payload)
                val fileName = ReportFileNames.backupFileName(Instant.ofEpochMilli(exportedAt))
                val file = fileWriter.writeText(fileName, json)
                ShareFileRequest(file, "application/json")
            }
            backupPreferences.lastExportEpochMillis = exportedAt
            _shareRequests.emit(request)
            _uiState.update { it.copy(isExporting = false) }
        }
    }

    private fun onRestoreFilePicked(content: String) {
        if (_uiState.value.isRestoring) return // re-entrancy guard
        _uiState.update { it.copy(isValidatingRestore = true) }
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.Default) { BackupValidator.validate(content) }) {
                is BackupValidationResult.Valid ->
                    _uiState.update {
                        it.copy(isValidatingRestore = false, pendingRestore = PendingRestore(result.payload, result.counts))
                    }
                is BackupValidationResult.Invalid ->
                    _uiState.update {
                        it.copy(isValidatingRestore = false, restoreFailure = RestoreFailure.Validation(result.error))
                    }
            }
        }
    }

    private fun onConfirmRestore() {
        val pending = _uiState.value.pendingRestore ?: return
        if (_uiState.value.isRestoring) return // re-entrancy guard
        _uiState.update { it.copy(isRestoring = true, pendingRestore = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { restoreWriter.restore(pending.payload) }
                _uiState.update { it.copy(isRestoring = false, restoreSuccess = pending.counts) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                // The restore transaction rolled back on failure (RestoreWriter's KDoc contract),
                // so the DB is untouched — the user just needs to know the file wasn't applied.
                _uiState.update { it.copy(isRestoring = false, restoreFailure = RestoreFailure.WriteFailed) }
            }
        }
    }

    companion object {
        fun factory(
            backupExporter: BackupExporter,
            fileWriter: ReportFileWriter,
            restoreWriter: RestoreWriter,
            backupPreferences: BackupPreferences,
            appVersionName: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BackupViewModel(
                    backupExporter,
                    fileWriter,
                    restoreWriter,
                    backupPreferences,
                    appVersionName,
                )
            }
        }
    }
}
