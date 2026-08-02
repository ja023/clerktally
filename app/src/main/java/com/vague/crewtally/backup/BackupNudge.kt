package com.vague.crewtally.backup

/**
 * Pure threshold logic for the More screen's backup nudge banner (LOCKED Phase 5: "gentle
 * non-blocking banner... if no export in 30 days", dismissible per occurrence, reappears on the
 * next 30-day lapse, with a Settings toggle to turn it off entirely). No Android dependency, so
 * the whole rule is unit-testable against plain epoch-millis inputs.
 *
 * The "reappears next lapse" behavior comes from [BackupPreferences.dismissedForReference]:
 * dismissing the banner records the CURRENT [lastExportEpochMillis] (or [NEVER_EXPORTED_REFERENCE]
 * when nothing has ever been exported) as "already dismissed for this state". The banner stays
 * hidden until that reference value changes — i.e. until a new export happens — at which point a
 * fresh 30-day clock starts and the banner can show again once it lapses.
 */
object BackupNudge {
    const val LAPSE_THRESHOLD_MILLIS = 30L * 24 * 60 * 60 * 1000
    const val NEVER_EXPORTED_REFERENCE = -1L

    fun shouldShow(
        lastExportEpochMillis: Long?,
        nowEpochMillis: Long,
        nudgeEnabled: Boolean,
        dismissedForReference: Long?,
    ): Boolean {
        if (!nudgeEnabled) return false

        val currentReference = lastExportEpochMillis ?: NEVER_EXPORTED_REFERENCE
        if (dismissedForReference == currentReference) return false

        val lapsed = lastExportEpochMillis == null ||
            (nowEpochMillis - lastExportEpochMillis) > LAPSE_THRESHOLD_MILLIS
        return lapsed
    }
}
