package com.vague.crewtally.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupSerializerTest {

    private fun samplePayload() = BackupPayload(
        schemaVersion = BackupPayload.CURRENT_SCHEMA_VERSION,
        exportedAt = 1_700_000_000_000L,
        appVersionName = "0.1.0",
        companies = listOf(CompanyDto("co1", "Acme", "Jane", "555-1234", "notes", archived = false)),
        clerks = listOf(ClerkDto("cl1", "Ali", "555-9999", "", active = true)),
        projects = listOf(ProjectDto("p1", "co1", "Warehouse", "Beirut", "2026-01-01", null, "ACTIVE", "USD", "")),
        rosterEntries = listOf(RosterEntryDto("r1", "p1", "cl1", 5000, null)),
        attendanceEntries = listOf(AttendanceEntryDto("a1", "p1", "cl1", "2026-01-02", true, 5000, true)),
        extraPayLines = listOf(ExtraPayLineDto("x1", "a1", "Bonus", 1000)),
        payments = listOf(PaymentDto("pay1", "p1", "cl1", "2026-01-03", 2000, "note")),
    )

    @Test
    fun `encoding then decoding a payload round-trips to an identical value`() {
        val original = samplePayload()

        val decoded = BackupSerializer.decode(BackupSerializer.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `decoding malformed JSON returns null instead of throwing`() {
        assertNull(BackupSerializer.decode("{ this is not valid json"))
    }
}
