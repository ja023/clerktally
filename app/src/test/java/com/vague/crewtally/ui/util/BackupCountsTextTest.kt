package com.vague.crewtally.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** The pure sentence-assembly half of [backupCountsSentence] — see that function's KDoc. */
class BackupCountsTextTest {

    @Test
    fun `joins every phrase with a comma and space, preserving order`() {
        val sentence = assembleBackupCountsSentence(
            listOf("1 company", "3 clerks", "0 projects", "2 roster entries", "5 attendance records", "1 extra-pay line", "4 payments"),
        )

        assertEquals(
            "1 company, 3 clerks, 0 projects, 2 roster entries, 5 attendance records, 1 extra-pay line, 4 payments",
            sentence,
        )
    }

    @Test
    fun `a single phrase is returned unchanged`() {
        assertEquals("1 company", assembleBackupCountsSentence(listOf("1 company")))
    }
}
