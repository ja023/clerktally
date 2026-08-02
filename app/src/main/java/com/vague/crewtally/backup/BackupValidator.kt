package com.vague.crewtally.backup

import java.time.LocalDate

/**
 * Every way [BackupValidator] can reject a file, each carrying enough detail for the UI layer to
 * build a specific, plain-English message (LOCKED: "reject with a clear error message naming the
 * problem, never a stack trace"). Deliberately NOT holding pre-formatted English strings itself —
 * this package has no [android.content.Context], so the caller resolves each case against
 * `strings.xml` (mirroring [com.vague.crewtally.report.ReportStrings]'s reasoning).
 */
sealed interface BackupValidationError {
    /** The file could not be parsed as CrewTally backup JSON at all. */
    data object MalformedFile : BackupValidationError

    /** [found] is not a schema version this build knows how to restore. */
    data class UnsupportedSchemaVersion(val found: Int, val supported: Int) : BackupValidationError

    /** A required field was blank/unparsable somewhere in the payload; [field] names it plainly. */
    data class MissingField(val field: String) : BackupValidationError

    /** A foreign key inside the payload points at a row that isn't present in the same payload. */
    data class DanglingReference(val reference: String) : BackupValidationError
}

/** The outcome of validating a raw backup file's text content. */
sealed interface BackupValidationResult {
    data class Valid(val payload: BackupPayload, val counts: BackupCounts) : BackupValidationResult
    data class Invalid(val error: BackupValidationError) : BackupValidationResult
}

/**
 * Fully validates a backup file's raw text BEFORE anything touches the database (LOCKED: "import
 * validates before touching the DB"). Pure Kotlin — no Room, no file I/O — so every rejection
 * path is unit-testable against a hand-built JSON string.
 */
object BackupValidator {

    fun validate(raw: String): BackupValidationResult {
        val payload = BackupSerializer.decode(raw)
            ?: return BackupValidationResult.Invalid(BackupValidationError.MalformedFile)

        if (payload.schemaVersion != BackupPayload.CURRENT_SCHEMA_VERSION) {
            return BackupValidationResult.Invalid(
                BackupValidationError.UnsupportedSchemaVersion(payload.schemaVersion, BackupPayload.CURRENT_SCHEMA_VERSION),
            )
        }

        requiredFieldError(payload)?.let { return BackupValidationResult.Invalid(it) }
        danglingReferenceError(payload)?.let { return BackupValidationResult.Invalid(it) }

        return BackupValidationResult.Valid(payload, BackupCounts.of(payload))
    }

    private fun requiredFieldError(payload: BackupPayload): BackupValidationError.MissingField? {
        payload.companies.forEach { company ->
            if (company.id.isBlank()) return BackupValidationError.MissingField("company id")
            if (company.name.isBlank()) return BackupValidationError.MissingField("company name")
        }
        payload.clerks.forEach { clerk ->
            if (clerk.id.isBlank()) return BackupValidationError.MissingField("clerk id")
            if (clerk.name.isBlank()) return BackupValidationError.MissingField("clerk name")
        }
        payload.projects.forEach { project ->
            if (project.id.isBlank()) return BackupValidationError.MissingField("project id")
            if (project.name.isBlank()) return BackupValidationError.MissingField("project name")
            if (project.currency.isBlank()) return BackupValidationError.MissingField("project currency")
            if (!isValidDate(project.startDate)) return BackupValidationError.MissingField("project start date")
            if (project.endDate != null && !isValidDate(project.endDate)) {
                return BackupValidationError.MissingField("project end date")
            }
        }
        payload.rosterEntries.forEach { roster ->
            if (roster.id.isBlank()) return BackupValidationError.MissingField("roster entry id")
        }
        payload.attendanceEntries.forEach { attendance ->
            if (attendance.id.isBlank()) return BackupValidationError.MissingField("attendance entry id")
            if (!isValidDate(attendance.date)) return BackupValidationError.MissingField("attendance date")
        }
        payload.extraPayLines.forEach { extra ->
            if (extra.id.isBlank()) return BackupValidationError.MissingField("extra-pay line id")
        }
        payload.payments.forEach { payment ->
            if (payment.id.isBlank()) return BackupValidationError.MissingField("payment id")
            if (!isValidDate(payment.date)) return BackupValidationError.MissingField("payment date")
        }
        return null
    }

    private fun danglingReferenceError(payload: BackupPayload): BackupValidationError.DanglingReference? {
        val companyIds = payload.companies.mapTo(HashSet()) { it.id }
        val clerkIds = payload.clerks.mapTo(HashSet()) { it.id }
        val projectIds = payload.projects.mapTo(HashSet()) { it.id }
        val attendanceIds = payload.attendanceEntries.mapTo(HashSet()) { it.id }

        payload.projects.forEach { project ->
            if (project.companyId !in companyIds) return BackupValidationError.DanglingReference("project -> company")
        }
        payload.rosterEntries.forEach { roster ->
            if (roster.projectId !in projectIds) return BackupValidationError.DanglingReference("roster entry -> project")
            if (roster.clerkId !in clerkIds) return BackupValidationError.DanglingReference("roster entry -> clerk")
        }
        payload.attendanceEntries.forEach { attendance ->
            if (attendance.projectId !in projectIds) return BackupValidationError.DanglingReference("attendance entry -> project")
            if (attendance.clerkId !in clerkIds) return BackupValidationError.DanglingReference("attendance entry -> clerk")
        }
        payload.extraPayLines.forEach { extra ->
            if (extra.attendanceEntryId !in attendanceIds) {
                return BackupValidationError.DanglingReference("extra-pay line -> attendance entry")
            }
        }
        payload.payments.forEach { payment ->
            if (payment.projectId !in projectIds) return BackupValidationError.DanglingReference("payment -> project")
            if (payment.clerkId !in clerkIds) return BackupValidationError.DanglingReference("payment -> clerk")
        }
        return null
    }

    private fun isValidDate(value: String): Boolean = runCatching { LocalDate.parse(value) }.isSuccess
}
