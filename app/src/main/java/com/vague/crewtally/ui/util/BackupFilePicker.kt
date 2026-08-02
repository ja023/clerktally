package com.vague.crewtally.ui.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException

private const val MAX_BACKUP_FILE_BYTES = 20L * 1024 * 1024

/**
 * Outcome of reading a SAF-picked backup file, BEFORE its content ever reaches
 * [com.vague.crewtally.backup.BackupValidator] — that validator only ever sees [Content]'s text,
 * so the two rejection shapes here ([TooLarge]/[Unreadable]) need their own distinct messages
 * rather than falling through to `BackupValidationError.MalformedFile`, which means "read fine,
 * but not valid JSON" and would otherwise be misleading for either case.
 */
sealed interface BackupFileReadOutcome {
    data class Content(val text: String) : BackupFileReadOutcome
    data object TooLarge : BackupFileReadOutcome
    data object Unreadable : BackupFileReadOutcome
}

/**
 * Reads a SAF-picked backup file off the main thread. Checks the file's size via
 * [android.content.ContentResolver] BEFORE reading any of it, so an oversized file never gets
 * fully buffered into memory first (LOCKED: reject > 20MB with a clear message rather than
 * risking an OOM read). A read that fails outright (permission revoked mid-flight, provider
 * gone) is [Unreadable], distinct from a file that reads fine but isn't valid backup JSON.
 */
fun readBackupFile(context: Context, uri: Uri): BackupFileReadOutcome {
    val size = queryFileSize(context, uri)
    if (size != null && size > MAX_BACKUP_FILE_BYTES) return BackupFileReadOutcome.TooLarge

    return try {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return BackupFileReadOutcome.Unreadable
        BackupFileReadOutcome.Content(text)
    } catch (e: SecurityException) {
        BackupFileReadOutcome.Unreadable
    } catch (e: IOException) {
        BackupFileReadOutcome.Unreadable
    }
}

/** Tries [android.content.res.AssetFileDescriptor.getLength] first, falls back to [OpenableColumns.SIZE]; null if neither knows. */
private fun queryFileSize(context: Context, uri: Uri): Long? {
    runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            if (descriptor.length >= 0) return descriptor.length
        }
    }
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) return cursor.getLong(sizeIndex)
        }
    }
    return null
}
