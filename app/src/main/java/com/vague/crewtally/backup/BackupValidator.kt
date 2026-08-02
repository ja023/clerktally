package com.vague.crewtally.backup

import com.vague.crewtally.data.local.ProjectStatus
import com.vague.crewtally.util.Money
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

    /** Two or more rows in [table] share the same id — every table's id must be unique. */
    data class DuplicateId(val table: String) : BackupValidationError

    /** A rate/amount [field] is negative where negative isn't legitimate, or too large to trust. */
    data class InvalidAmount(val field: String) : BackupValidationError
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
        duplicateIdError(payload)?.let { return BackupValidationResult.Invalid(it) }
        invalidAmountError(payload)?.let { return BackupValidationResult.Invalid(it) }
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
            if (!isValidStatus(project.status)) return BackupValidationError.MissingField("project status")
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

    /**
     * Every table's ids must be unique — a duplicate would silently overwrite a row during
     * [RestoreWriter]'s upsert-by-id pass, quietly dropping data instead of failing loudly here.
     */
    private fun duplicateIdError(payload: BackupPayload): BackupValidationError.DuplicateId? {
        val tables = listOf(
            "companies" to payload.companies.map { it.id },
            "clerks" to payload.clerks.map { it.id },
            "projects" to payload.projects.map { it.id },
            "roster entries" to payload.rosterEntries.map { it.id },
            "attendance records" to payload.attendanceEntries.map { it.id },
            "extra-pay lines" to payload.extraPayLines.map { it.id },
            "payments" to payload.payments.map { it.id },
        )
        tables.forEach { (table, ids) ->
            if (ids.toSet().size != ids.size) return BackupValidationError.DuplicateId(table)
        }
        return null
    }

    /**
     * Rates and payment amounts must be non-negative and within [Money.MAX_MINOR_UNITS] — extras
     * are the one legitimately negative amount (LOCKED #7: deductions), so they're bounded by
     * absolute value only, never rejected for being negative.
     */
    private fun invalidAmountError(payload: BackupPayload): BackupValidationError.InvalidAmount? {
        payload.rosterEntries.forEach { roster ->
            if (!isValidAmount(roster.dailyRate, allowNegative = false)) {
                return BackupValidationError.InvalidAmount("roster entry rate")
            }
        }
        payload.attendanceEntries.forEach { attendance ->
            if (!isValidAmount(attendance.rateSnapshot, allowNegative = false)) {
                return BackupValidationError.InvalidAmount("attendance rate snapshot")
            }
        }
        payload.extraPayLines.forEach { extra ->
            if (!isValidAmount(extra.amount, allowNegative = true)) {
                return BackupValidationError.InvalidAmount("extra-pay line amount")
            }
        }
        payload.payments.forEach { payment ->
            if (!isValidAmount(payment.amount, allowNegative = false)) {
                return BackupValidationError.InvalidAmount("payment amount")
            }
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

    private fun isValidStatus(value: String): Boolean = runCatching { ProjectStatus.valueOf(value) }.isSuccess

    private fun isValidAmount(value: Long, allowNegative: Boolean): Boolean {
        if (!allowNegative && value < 0) return false
        if (value == Long.MIN_VALUE) return false // would overflow kotlin.math.abs below
        return kotlin.math.abs(value) <= Money.MAX_MINOR_UNITS
    }
}
