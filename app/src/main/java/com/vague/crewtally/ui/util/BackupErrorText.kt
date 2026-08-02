package com.vague.crewtally.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vague.crewtally.R
import com.vague.crewtally.backup.BackupValidationError
import com.vague.crewtally.ui.viewmodel.RestoreFailure

/**
 * The one shared phrasing for why a restore didn't happen, reused by the Backup screen's failure
 * dialog — mirrors [owedDisplayText]'s pattern of resolving a plain-Kotlin result type into a
 * complete, specific sentence (LOCKED: "reject with a clear error message naming the problem,
 * never a stack trace").
 */
@Composable
fun restoreFailureText(failure: RestoreFailure): String = when (failure) {
    is RestoreFailure.WriteFailed -> stringResource(R.string.backup_restore_error_write_failed)
    is RestoreFailure.FileTooLarge -> stringResource(R.string.backup_restore_error_too_large)
    is RestoreFailure.FileUnreadable -> stringResource(R.string.backup_restore_error_unreadable)
    is RestoreFailure.Validation -> when (val error = failure.error) {
        BackupValidationError.MalformedFile -> stringResource(R.string.backup_restore_error_malformed)
        is BackupValidationError.UnsupportedSchemaVersion -> stringResource(R.string.backup_restore_error_schema_version)
        is BackupValidationError.MissingField -> stringResource(R.string.backup_restore_error_missing_field, error.field)
        is BackupValidationError.DanglingReference ->
            stringResource(R.string.backup_restore_error_dangling_reference, error.reference)
        is BackupValidationError.DuplicateId -> stringResource(R.string.backup_restore_error_duplicate_id, error.table)
        is BackupValidationError.InvalidAmount -> stringResource(R.string.backup_restore_error_invalid_amount, error.field)
    }
}
