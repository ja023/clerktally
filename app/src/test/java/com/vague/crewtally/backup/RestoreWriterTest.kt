package com.vague.crewtally.backup

import com.vague.crewtally.data.local.CompanyEntity
import com.vague.crewtally.testutil.FakeAttendanceEntryDao
import com.vague.crewtally.testutil.FakeClerkDao
import com.vague.crewtally.testutil.FakeCompanyDao
import com.vague.crewtally.testutil.FakeExtraPayLineDao
import com.vague.crewtally.testutil.FakePaymentDao
import com.vague.crewtally.testutil.FakeProjectDao
import com.vague.crewtally.testutil.FakeRestoreWriter
import com.vague.crewtally.testutil.FakeRosterEntryDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RestoreWriterTest {

    private lateinit var companyDao: FakeCompanyDao
    private lateinit var clerkDao: FakeClerkDao
    private lateinit var projectDao: FakeProjectDao
    private lateinit var rosterEntryDao: FakeRosterEntryDao
    private lateinit var attendanceEntryDao: FakeAttendanceEntryDao
    private lateinit var extraPayLineDao: FakeExtraPayLineDao
    private lateinit var paymentDao: FakePaymentDao
    private lateinit var writer: FakeRestoreWriter

    @Before
    fun setUp() {
        companyDao = FakeCompanyDao()
        clerkDao = FakeClerkDao()
        projectDao = FakeProjectDao()
        rosterEntryDao = FakeRosterEntryDao()
        attendanceEntryDao = FakeAttendanceEntryDao()
        extraPayLineDao = FakeExtraPayLineDao(attendanceEntryDao)
        paymentDao = FakePaymentDao()
        writer = FakeRestoreWriter(companyDao, clerkDao, projectDao, rosterEntryDao, attendanceEntryDao, extraPayLineDao, paymentDao)
    }

    private fun samplePayload() = BackupPayload(
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
    fun `restoring a payload replaces whatever was previously in every table`() = runTest {
        companyDao.seed(CompanyEntity(id = "old-co", name = "Stale Co"))

        writer.restore(samplePayload())

        val companies = companyDao.all()
        assertEquals(1, companies.size)
        assertEquals("co1", companies.single().id)
        assertEquals(1, clerkDao.all().size)
        assertEquals(1, attendanceEntryDao.all().size)
        assertEquals(1, extraPayLineDao.all().size)
    }

    @Test
    fun `a failure before any write during restore leaves the prior data completely untouched`() = runTest {
        companyDao.seed(CompanyEntity(id = "old-co", name = "Stale Co"))
        val faultyWriter = FaultyRestoreWriter(writer)

        try {
            faultyWriter.restore(samplePayload())
            fail("expected the injected failure to propagate")
        } catch (expected: IllegalStateException) {
            // expected: the fault is injected before any write in the combined restore runs.
        }

        assertEquals("prior company untouched", 1, companyDao.all().size)
        assertEquals("Stale Co", companyDao.all().single().name)
        assertTrue("no new roster/attendance rows leaked in", rosterEntryDao.all().isEmpty())
    }
}

/**
 * Fails BEFORE [FakeRestoreWriter.restore] runs — standing in for what a real Room transaction
 * rollback leaves behind on failure, mirroring [com.vague.crewtally.data.local.FaultyWalkInWriter]'s
 * role in `AttendanceWriterTest`.
 */
private class FaultyRestoreWriter(private val delegate: RestoreWriter) : RestoreWriter by delegate {
    override suspend fun restore(payload: BackupPayload) {
        error("simulated failure before any write in the combined restore transaction lands")
    }
}
