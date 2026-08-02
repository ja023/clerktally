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
import com.vague.crewtally.data.local.AttendanceEntryDao
import com.vague.crewtally.data.local.ClerkDao
import com.vague.crewtally.data.local.CompanyDao
import com.vague.crewtally.data.local.ExtraPayLineDao
import com.vague.crewtally.data.local.PaymentDao
import com.vague.crewtally.data.local.ProjectDao
import com.vague.crewtally.data.local.RosterEntryDao
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

/** Why a restore did not complete — validation rejected the file, or the write itself failed. */
sealed interface RestoreFailure {
    data class Validation(val error: BackupValidationError) : RestoreFailure
    data object WriteFailed : RestoreFailure
}

data class BackupUiState(
    val isExporting: Boolean = false,
    val isRestoring: Boolean = false,
    val pendingRestore: PendingRestore? = null,
    val restoreFailure: RestoreFailure? = null,
    val restoreSuccess: BackupCounts? = null,
)

sealed interface BackupEvent {
    data object ExportRequested : BackupEvent

    /** [content] is the already-read text of the file the user picked via SAF — read by the screen, not this ViewModel. */
    data class RestoreFilePicked(val content: String) : BackupEvent
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
 * guards, matching [ClerkStatementShareViewModel]'s pattern.
 */
class BackupViewModel(
    private val companyDao: CompanyDao,
    private val clerkDao: ClerkDao,
    private val projectDao: ProjectDao,
    private val rosterEntryDao: RosterEntryDao,
    private val attendanceEntryDao: AttendanceEntryDao,
    private val extraPayLineDao: ExtraPayLineDao,
    private val paymentDao: PaymentDao,
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
                val payload = BackupExporter.export(
                    companyDao,
                    clerkDao,
                    projectDao,
                    rosterEntryDao,
                    attendanceEntryDao,
                    extraPayLineDao,
                    paymentDao,
                    appVersionName,
                    exportedAt,
                )
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
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.Default) { BackupValidator.validate(content) }) {
                is BackupValidationResult.Valid ->
                    _uiState.update { it.copy(pendingRestore = PendingRestore(result.payload, result.counts)) }
                is BackupValidationResult.Invalid ->
                    _uiState.update { it.copy(restoreFailure = RestoreFailure.Validation(result.error)) }
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
            companyDao: CompanyDao,
            clerkDao: ClerkDao,
            projectDao: ProjectDao,
            rosterEntryDao: RosterEntryDao,
            attendanceEntryDao: AttendanceEntryDao,
            extraPayLineDao: ExtraPayLineDao,
            paymentDao: PaymentDao,
            fileWriter: ReportFileWriter,
            restoreWriter: RestoreWriter,
            backupPreferences: BackupPreferences,
            appVersionName: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BackupViewModel(
                    companyDao,
                    clerkDao,
                    projectDao,
                    rosterEntryDao,
                    attendanceEntryDao,
                    extraPayLineDao,
                    paymentDao,
                    fileWriter,
                    restoreWriter,
                    backupPreferences,
                    appVersionName,
                )
            }
        }
    }
}
