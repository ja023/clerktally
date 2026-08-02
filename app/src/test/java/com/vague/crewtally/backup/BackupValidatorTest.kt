package com.vague.crewtally.backup

import com.vague.crewtally.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every rejection path [BackupValidator] must catch BEFORE anything touches the database
 * (LOCKED: "import validates before touching the DB"), plus the happy path's row counts.
 */
class BackupValidatorTest {

    private fun validPayload() = BackupPayload(
        schemaVersion = BackupPayload.CURRENT_SCHEMA_VERSION,
        exportedAt = 1_700_000_000_000L,
        appVersionName = "0.1.0",
        companies = listOf(CompanyDto("co1", "Acme", "", "", "", archived = false)),
        clerks = listOf(ClerkDto("cl1", "Ali", "", "", active = true)),
        projects = listOf(ProjectDto("p1", "co1", "Warehouse", "", "2026-01-01", null, "ACTIVE", "USD", "")),
        rosterEntries = listOf(RosterEntryDto("r1", "p1", "cl1", 5000, null)),
        attendanceEntries = listOf(AttendanceEntryDto("a1", "p1", "cl1", "2026-01-02", true, 5000, true)),
        extraPayLines = listOf(ExtraPayLineDto("x1", "a1", "Bonus", 1000)),
        payments = listOf(PaymentDto("pay1", "p1", "cl1", "2026-01-03", 2000, "")),
    )

    @Test
    fun `a well-formed payload is accepted with counts matching every table's row count`() {
        val result = BackupValidator.validate(BackupSerializer.encode(validPayload()))

        assertTrue(result is BackupValidationResult.Valid)
        val counts = (result as BackupValidationResult.Valid).counts
        assertEquals(1, counts.companies)
        assertEquals(1, counts.clerks)
        assertEquals(1, counts.projects)
        assertEquals(1, counts.rosterEntries)
        assertEquals(1, counts.attendanceEntries)
        assertEquals(1, counts.extraPayLines)
        assertEquals(1, counts.payments)
    }

    @Test
    fun `malformed JSON is rejected as MalformedFile`() {
        val result = BackupValidator.validate("not json at all")

        assertEquals(BackupValidationError.MalformedFile, (result as BackupValidationResult.Invalid).error)
    }

    @Test
    fun `an unsupported schema version is rejected naming the found and supported versions`() {
        val payload = validPayload().copy(schemaVersion = 99)

        val result = BackupValidator.validate(BackupSerializer.encode(payload)) as BackupValidationResult.Invalid

        val error = result.error as BackupValidationError.UnsupportedSchemaVersion
        assertEquals(99, error.found)
        assertEquals(BackupPayload.CURRENT_SCHEMA_VERSION, error.supported)
    }

    @Test
    fun `a blank required field is rejected as MissingField naming which field`() {
        val payload = validPayload().copy(clerks = listOf(ClerkDto("cl1", "", "", "", active = true)))

        val result = BackupValidator.validate(BackupSerializer.encode(payload)) as BackupValidationResult.Invalid

        assertEquals(BackupValidationError.MissingField("clerk name"), result.error)
    }

    @Test
    fun `an unparsable project start date is rejected as MissingField`() {
        val payload = validPayload().copy(
            projects = listOf(ProjectDto("p1", "co1", "Warehouse", "", "not-a-date", null, "ACTIVE", "USD", "")),
        )

        val result = BackupValidator.validate(BackupSerializer.encode(payload)) as BackupValidationResult.Invalid

        assertEquals(BackupValidationError.MissingField("project start date"), result.error)
    }

    @Test
    fun `a roster entry pointing at a clerk that isn't in the payload is a dangling reference`() {
        val payload = validPayload().copy(
            rosterEntries = listOf(RosterEntryDto("r1", "p1", "no-such-clerk", 5000, null)),
        )

        val result = BackupValidator.validate(BackupSerializer.encode(payload)) as BackupValidationResult.Invalid

        assertEquals(BackupValidationError.DanglingReference("roster entry -> clerk"), result.error)
    }

    @Test
    fun `an extra-pay line pointing at a missing attendance entry is a dangling reference`() {
        val payload = validPayload().copy(
            extraPayLines = listOf(ExtraPayLineDto("x1", "no-such-attendance", "Bonus", 1000)),
        )

        val result = BackupValidator.validate(BackupSerializer.encode(payload)) as BackupValidationResult.Invalid

        assertEquals(BackupValidationError.DanglingReference("extra-pay line -> attendance entry"), result.error)
    }

    @Test
    fun `a project pointing at a missing company is a dangling reference`() {
        val payload = validPayload().copy(
            projects = listOf(ProjectDto("p1", "no-such-company", "Warehouse", "", "2026-01-01", null, "ACTIVE", "USD", "")),
        )

        val result = BackupValidator.validate(BackupSerializer.encode(payload)) as BackupValidationResult.Invalid

        assertEquals(BackupValidationError.DanglingReference("project -> company"), result.error)
    }

    @Test
    fun `a payment pointing at a missing project is a dangling reference`() {
        val payload = validPayload().copy(
            payments = listOf(PaymentDto("pay1", "no-such-project", "cl1", "2026-01-03", 2000, "")),
        )

        val result = BackupValidator.validate(BackupSerializer.encode(payload)) as BackupValidationResult.Invalid

        assertEquals(BackupValidationError.DanglingReference("payment -> project"), result.error)
    }

    @Test
    fun `an unrecognized project status is rejected as MissingField naming project status`() {
        val payload = validPayload().copy(
            projects = listOf(ProjectDto("p1", "co1", "Warehouse", "", "2026-01-01", null, "ACTIVEE", "USD", "")),
        )

        val result = BackupValidator.validate(BackupSerializer.encode(payload)) as BackupValidationResult.Invalid

        assertEquals(BackupValidationError.MissingField("project status"), result.error)
    }

    @Test
    fun `two companies sharing the same id are rejected as a duplicate id in companies`() {
        val payload = validPayload().copy(
            companies = listOf(CompanyDto("co1", "Acme", "", "", "", archived = false), CompanyDto("co1", "Acme Two", "", "", "", archived = false)),
        )

        val result = BackupValidator.validate(BackupSerializer.encode(payload)) as BackupValidationResult.Invalid

        assertEquals(BackupValidationError.DuplicateId("companies"), result.error)
    }

    @Test
    fun `two payments sharing the same id are rejected as a duplicate id in payments`() {
        val payload = validPayload().copy(
            payments = listOf(
                PaymentDto("pay1", "p1", "cl1", "2026-01-03", 2000, ""),
                PaymentDto("pay1", "p1", "cl1", "2026-01-04", 1000, ""),
            ),
        )

        val result = BackupValidator.validate(BackupSerializer.encode(payload)) as BackupValidationResult.Invalid

        assertEquals(BackupValidationError.DuplicateId("payments"), result.error)
    }

    @Test
    fun `a negative roster daily rate is rejected as an invalid amount`() {
        val payload = validPayload().copy(rosterEntries = listOf(RosterEntryDto("r1", "p1", "cl1", -500, null)))

        val result = BackupValidator.validate(BackupSerializer.encode(payload)) as BackupValidationResult.Invalid

        assertEquals(BackupValidationError.InvalidAmount("roster entry rate"), result.error)
    }

    @Test
    fun `a negative payment amount is rejected as an invalid amount`() {
        val payload = validPayload().copy(payments = listOf(PaymentDto("pay1", "p1", "cl1", "2026-01-03", -2000, "")))

        val result = BackupValidator.validate(BackupSerializer.encode(payload)) as BackupValidationResult.Invalid

        assertEquals(BackupValidationError.InvalidAmount("payment amount"), result.error)
    }

    @Test
    fun `a negative extra-pay line is accepted as a legitimate deduction`() {
        val payload = validPayload().copy(extraPayLines = listOf(ExtraPayLineDto("x1", "a1", "Damage", -2000)))

        val result = BackupValidator.validate(BackupSerializer.encode(payload))

        assertTrue(result is BackupValidationResult.Valid)
    }

    @Test
    fun `an extra-pay line beyond the money ceiling is rejected as an invalid amount, deduction or not`() {
        val payload = validPayload().copy(
            extraPayLines = listOf(ExtraPayLineDto("x1", "a1", "Huge", -(Money.MAX_MINOR_UNITS + 1))),
        )

        val result = BackupValidator.validate(BackupSerializer.encode(payload)) as BackupValidationResult.Invalid

        assertEquals(BackupValidationError.InvalidAmount("extra-pay line amount"), result.error)
    }
}
