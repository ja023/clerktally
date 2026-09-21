package com.vague.crewtally.report

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportFileNamesTest {

    @Test
    fun `backup file name follows the crewtally-backup-YYYYMMDD-HHmm pattern in UTC`() {
        val instant = Instant.parse("2026-08-02T14:05:00Z")
        assertEquals("crewtally-backup-20260802-1405.json", ReportFileNames.backupFileName(instant, ZoneOffset.UTC))
    }

    @Test
    fun `clerk statement file name sanitizes unsafe characters and adds the suffix`() {
        assertEquals("Ali-Hassan-statement.txt", ReportFileNames.clerkStatementFileName("Ali/Hassan", "txt"))
    }

    @Test
    fun `company report file name handles a blank name with a safe fallback`() {
        assertEquals("report-report.pdf", ReportFileNames.companyReportFileName("   ", "pdf"))
    }

    @Test
    fun `the two cross-project clerk statements get distinct, bucket-tagged file names`() {
        assertEquals(
            "Ali-Hassan-active-statement.pdf",
            ReportFileNames.clerkBucketStatementFileName("Ali Hassan", ClerkProjectBucket.ACTIVE, "pdf"),
        )
        assertEquals(
            "Ali-Hassan-history-statement.txt",
            ReportFileNames.clerkBucketStatementFileName("Ali Hassan", ClerkProjectBucket.HISTORY, "txt"),
        )
    }

    @Test
    fun `sanitizing collapses runs of unsafe characters into a single dash`() {
        assertEquals("A-B-statement.txt", ReportFileNames.clerkStatementFileName("A & B!!", "txt"))
    }
}
