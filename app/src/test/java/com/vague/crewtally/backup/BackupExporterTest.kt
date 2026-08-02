package com.vague.crewtally.backup

import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.data.local.CompanyEntity
import com.vague.crewtally.testutil.FakeAttendanceEntryDao
import com.vague.crewtally.testutil.FakeBackupExporter
import com.vague.crewtally.testutil.FakeClerkDao
import com.vague.crewtally.testutil.FakeCompanyDao
import com.vague.crewtally.testutil.FakeExtraPayLineDao
import com.vague.crewtally.testutil.FakePaymentDao
import com.vague.crewtally.testutil.FakeProjectDao
import com.vague.crewtally.testutil.FakeRosterEntryDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [FakeBackupExporter] mirrors [RoomBackupExporter]'s DAO-to-DTO assembly (see that fake's KDoc
 * for why the real transaction wrapping isn't exercised here — same JVM-vs-Room gap as
 * [RestoreWriterTest]).
 */
class BackupExporterTest {

    @Test
    fun `export reads every table's rows verbatim into the payload with the given metadata`() = runTest {
        val companyDao = FakeCompanyDao().apply { seed(CompanyEntity(id = "co1", name = "Acme")) }
        val clerkDao = FakeClerkDao().apply { seed(ClerkEntity(id = "cl1", name = "Ali")) }
        val projectDao = FakeProjectDao()
        val rosterEntryDao = FakeRosterEntryDao()
        val attendanceEntryDao = FakeAttendanceEntryDao()
        val extraPayLineDao = FakeExtraPayLineDao(attendanceEntryDao)
        val paymentDao = FakePaymentDao()
        val exporter = FakeBackupExporter(
            companyDao,
            clerkDao,
            projectDao,
            rosterEntryDao,
            attendanceEntryDao,
            extraPayLineDao,
            paymentDao,
        )

        val payload = exporter.export(
            appVersionName = "0.1.0",
            exportedAtEpochMillis = 1_700_000_000_000L,
        )

        assertEquals(BackupPayload.CURRENT_SCHEMA_VERSION, payload.schemaVersion)
        assertEquals(1_700_000_000_000L, payload.exportedAt)
        assertEquals("0.1.0", payload.appVersionName)
        assertEquals(listOf(CompanyDto("co1", "Acme", "", "", "", archived = false)), payload.companies)
        assertEquals(listOf(ClerkDto("cl1", "Ali", "", "", active = true)), payload.clerks)
    }
}
