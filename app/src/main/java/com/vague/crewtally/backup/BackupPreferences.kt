package com.vague.crewtally.backup

import android.content.Context

/**
 * Remembers, across app launches, when the last backup export happened, whether the 30-day
 * nudge is enabled, and which "last export" the user has already dismissed the nudge for —
 * backed by `SharedPreferences`, mirroring [com.vague.crewtally.data.contacts.ContactsPermissionStore]'s
 * pattern (a plain in-memory flag would forget on process death, and the nudge needs to survive
 * app restarts to mean anything).
 */
class BackupPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Epoch millis of the last successful export, or null if none has ever happened. */
    var lastExportEpochMillis: Long?
        get() = prefs.getLong(KEY_LAST_EXPORT, NO_VALUE).takeIf { it != NO_VALUE }
        set(value) {
            prefs.edit().putLong(KEY_LAST_EXPORT, value ?: NO_VALUE).apply()
        }

    /** Whether the 30-day nudge banner is enabled at all (a Settings toggle turns it off — LOCKED). */
    var nudgeEnabled: Boolean
        get() = prefs.getBoolean(KEY_NUDGE_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_NUDGE_ENABLED, value).apply()
        }

    /**
     * The `lastExportEpochMillis` value (or [BackupNudge.NEVER_EXPORTED_REFERENCE]) that was
     * current when the user last dismissed the nudge — see [BackupNudge.shouldShow] for how
     * this makes a dismissal reappear on the NEXT 30-day lapse rather than being gone forever.
     */
    var dismissedForReference: Long?
        get() = prefs.getLong(KEY_DISMISSED_FOR, NO_VALUE).takeIf { it != NO_VALUE }
        set(value) {
            prefs.edit().putLong(KEY_DISMISSED_FOR, value ?: NO_VALUE).apply()
        }

    private companion object {
        const val PREFS_NAME = "crewtally_backup_prefs"
        const val KEY_LAST_EXPORT = "last_export_epoch_millis"
        const val KEY_NUDGE_ENABLED = "nudge_enabled"
        const val KEY_DISMISSED_FOR = "dismissed_for_reference"
        const val NO_VALUE = -1L
    }
}
