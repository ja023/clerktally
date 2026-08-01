package com.vague.crewtally.data.local

import com.vague.crewtally.testutil.FakeAttendanceEntryDao
import com.vague.crewtally.testutil.FakeAttendanceWriter
import com.vague.crewtally.testutil.FakeExtraPayLineDao
import com.vague.crewtally.testutil.FakeRosterEntryDao
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Contract-level tests for [AttendanceWriter], exercised against [FakeAttendanceWriter] (which
 * mirrors [RoomAttendanceWriter]'s documented semantics — see that class's KDoc) rather than
 * through any one screen's ViewModel: [clearAttendance]'s force flag and [saveWalkIn]'s
 * atomicity are writer-level guarantees every caller relies on, not something any single screen
 * should have to re-prove.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceWriterTest {

    private val projectId = "project-1"
    private val clerkId = "clerk-a"
    private val date = LocalDate.of(2026, 1, 15)

    private lateinit var attendanceEntryDao: FakeAttendanceEntryDao
    private lateinit var extraPayLineDao: FakeExtraPayLineDao
    private lateinit var rosterEntryDao: FakeRosterEntryDao
    private lateinit var writer: FakeAttendanceWriter

    @Before
    fun setUp() {
        attendanceEntryDao = FakeAttendanceEntryDao()
        extraPayLineDao = FakeExtraPayLineDao(attendanceEntryDao)
        attendanceEntryDao.extraPayLineDao = extraPayLineDao
        rosterEntryDao = FakeRosterEntryDao()
        writer = FakeAttendanceWriter(attendanceEntryDao, extraPayLineDao, rosterEntryDao)
    }

    @Test
    fun `a guarded clearAttendance call on a row with extras is blocked and deletes nothing`() = runTest {
        attendanceEntryDao.seed(
            AttendanceEntryEntity(id = "a1", projectId = projectId, clerkId = clerkId, date = date, present = true, rateSnapshot = 5000),
        )
        extraPayLineDao.seed(ExtraPayLineEntity(id = "x1", attendanceEntryId = "a1", label = "Lunch", amount = 1000))

        val result = writer.clearAttendance(projectId, clerkId, date)

        assertEquals(ClearAttendanceResult.BlockedByExtras, result)
        assertEquals("nothing deleted", 1, attendanceEntryDao.all().size)
        assertEquals("extras untouched", 1, extraPayLineDao.all().size)
    }

    @Test
    fun `a forced clearAttendance call deletes the row and cascades its extras`() = runTest {
        attendanceEntryDao.seed(
            AttendanceEntryEntity(id = "a1", projectId = projectId, clerkId = clerkId, date = date, present = true, rateSnapshot = 5000),
        )
        extraPayLineDao.seed(ExtraPayLineEntity(id = "x1", attendanceEntryId = "a1", label = "Lunch", amount = 1000))

        val result = writer.clearAttendance(projectId, clerkId, date, force = true)

        assertEquals(ClearAttendanceResult.Cleared, result)
        assertTrue("row deleted", attendanceEntryDao.all().isEmpty())
        assertTrue("extras cascade with the row", extraPayLineDao.all().isEmpty())
    }

    @Test
    fun `clearing a row with no extras is Cleared even without force`() = runTest {
        attendanceEntryDao.seed(
            AttendanceEntryEntity(id = "a1", projectId = projectId, clerkId = clerkId, date = date, present = true, rateSnapshot = 5000),
        )

        val result = writer.clearAttendance(projectId, clerkId, date)

        assertEquals(ClearAttendanceResult.Cleared, result)
        assertTrue(attendanceEntryDao.all().isEmpty())
    }

    @Test
    fun `clearing a day with no row at all is a no-op Cleared`() = runTest {
        val result = writer.clearAttendance(projectId, clerkId, date)

        assertEquals(ClearAttendanceResult.Cleared, result)
    }

    @Test
    fun `a successful walk-in save writes both the attendance row and the roster row`() = runTest {
        writer.saveWalkIn(projectId, clerkId, date, rateSnapshot = 4200, alsoAddToRoster = true)

        assertEquals(1, attendanceEntryDao.all().count { it.clerkId == clerkId })
        assertEquals(4200L, rosterEntryDao.getForPair(projectId, clerkId)?.dailyRate)
    }

    @Test
    fun `a day-only walk-in save writes the attendance row but no roster row`() = runTest {
        writer.saveWalkIn(projectId, clerkId, date, rateSnapshot = 4200, alsoAddToRoster = false)

        assertEquals(1, attendanceEntryDao.all().count { it.clerkId == clerkId })
        assertNull(rosterEntryDao.getForPair(projectId, clerkId))
    }

    @Test
    fun `a failure during the combined walk-in write leaves neither row behind`() = runTest {
        val faultyWriter = FaultyWalkInWriter(writer)

        try {
            faultyWriter.saveWalkIn(projectId, clerkId, date, rateSnapshot = 4200, alsoAddToRoster = true)
            fail("expected the injected failure to propagate")
        } catch (expected: IllegalStateException) {
            // expected: the fault is injected before either write runs.
        }

        assertTrue(
            "no attendance row must survive a failed combined write",
            attendanceEntryDao.all().none { it.clerkId == clerkId },
        )
        assertNull("no roster row must survive a failed combined write", rosterEntryDao.getForPair(projectId, clerkId))
    }
}

/**
 * Fails BEFORE either write inside [saveWalkIn] runs — standing in for what a real DB
 * transaction rollback leaves behind on failure — so the test above can assert the
 * caller-facing contract is "both rows or neither", never "whichever half happened to run
 * first". [RoomAttendanceWriter.saveWalkIn]'s single `withTransaction` is what makes that true
 * in production; this proves callers can rely on the contract without depending on Room.
 */
private class FaultyWalkInWriter(private val delegate: AttendanceWriter) : AttendanceWriter by delegate {
    override suspend fun saveWalkIn(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        rateSnapshot: Long,
        alsoAddToRoster: Boolean,
    ) {
        error("simulated failure before either write in the combined transaction lands")
    }
}
