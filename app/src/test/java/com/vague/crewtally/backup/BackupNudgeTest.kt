package com.vague.crewtally.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupNudgeTest {

    private val now = 1_700_000_000_000L
    private val thirtyOneDaysMillis = 31L * 24 * 60 * 60 * 1000
    private val tenDaysMillis = 10L * 24 * 60 * 60 * 1000

    @Test
    fun `never having exported shows the nudge`() {
        assertTrue(BackupNudge.shouldShow(lastExportEpochMillis = null, nowEpochMillis = now, nudgeEnabled = true, dismissedForReference = null))
    }

    @Test
    fun `a recent export hides the nudge`() {
        val lastExport = now - tenDaysMillis
        assertFalse(
            BackupNudge.shouldShow(lastExportEpochMillis = lastExport, nowEpochMillis = now, nudgeEnabled = true, dismissedForReference = null),
        )
    }

    @Test
    fun `an export more than 30 days ago shows the nudge`() {
        val lastExport = now - thirtyOneDaysMillis
        assertTrue(
            BackupNudge.shouldShow(lastExportEpochMillis = lastExport, nowEpochMillis = now, nudgeEnabled = true, dismissedForReference = null),
        )
    }

    @Test
    fun `disabling the nudge hides it even when never exported`() {
        assertFalse(BackupNudge.shouldShow(lastExportEpochMillis = null, nowEpochMillis = now, nudgeEnabled = false, dismissedForReference = null))
    }

    @Test
    fun `dismissing for the current reference hides the nudge`() {
        val lastExport = now - thirtyOneDaysMillis
        assertFalse(
            BackupNudge.shouldShow(
                lastExportEpochMillis = lastExport,
                nowEpochMillis = now,
                nudgeEnabled = true,
                dismissedForReference = lastExport,
            ),
        )
    }

    @Test
    fun `dismissing before never-exported uses the sentinel reference`() {
        assertFalse(
            BackupNudge.shouldShow(
                lastExportEpochMillis = null,
                nowEpochMillis = now,
                nudgeEnabled = true,
                dismissedForReference = BackupNudge.NEVER_EXPORTED_REFERENCE,
            ),
        )
    }

    @Test
    fun `a dismissal reappears once a NEW lapse starts after a fresh export`() {
        val staleDismissedReference = now - (thirtyOneDaysMillis * 2) // dismissed for an old, no-longer-current export
        val freshExport = now - thirtyOneDaysMillis // a newer export happened, but it's now stale again

        assertTrue(
            BackupNudge.shouldShow(
                lastExportEpochMillis = freshExport,
                nowEpochMillis = now,
                nudgeEnabled = true,
                dismissedForReference = staleDismissedReference,
            ),
        )
    }
}
