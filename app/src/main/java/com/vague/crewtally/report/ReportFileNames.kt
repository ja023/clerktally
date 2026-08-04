package com.vague.crewtally.report

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure filename builders, all clock-driven (never `System.currentTimeMillis()` internally) so
 * every name is deterministic and unit-testable. Names are sanitized to a safe, portable
 * character set — a clerk or company name may contain anything the free-text form fields allow
 * (slashes, emoji, etc.), none of which belongs in a filename handed to the system share sheet.
 */
object ReportFileNames {
    private val BACKUP_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT)
    private val UNSAFE_CHARS = Regex("[^A-Za-z0-9]+")

    /** `crewtally-backup-YYYYMMDD-HHmm.json`, using [instant] via [zone] (defaults to device-local). */
    fun backupFileName(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        "crewtally-backup-${BACKUP_TIMESTAMP_FORMAT.format(instant.atZone(zone))}.json"

    fun clerkStatementFileName(clerkName: String, extension: String): String =
        "${sanitize(clerkName)}-statement.$extension"

    fun companyReportFileName(companyName: String, extension: String): String =
        "${sanitize(companyName)}-report.$extension"

    fun projectStatementFileName(projectName: String, extension: String): String =
        "${sanitize(projectName)}-statement.$extension"

    private fun sanitize(name: String): String {
        val cleaned = name.trim().replace(UNSAFE_CHARS, "-").trim('-')
        return cleaned.ifBlank { "report" }
    }
}
