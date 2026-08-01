package com.vague.crewtally.ui.viewmodel

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ExtraPayLineEntity
import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.data.local.RosterEntryEntity
import com.vague.crewtally.testutil.FakeAttendanceEntryDao
import com.vague.crewtally.testutil.FakeAttendanceWriter
import com.vague.crewtally.testutil.FakeCompanyDao
import com.vague.crewtally.testutil.FakeExtraPayLineDao
import com.vague.crewtally.testutil.FakeProjectDao
import com.vague.crewtally.testutil.FakeRosterEntryDao
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Headless JVM tests for [AttendanceDayViewModel] — the attendance daily loop. Covers the
 * three-state transitions and their auto-save writes, the extras cascade guard on unmark,
 * mark-all-present skipping explicit absents, the future-days-only rateSnapshot rule, the
 * walk-in row union, the backfill-date warning, and the rapid-toggle race settling as one row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceDayViewModelTest {

    private val projectId = "project-1"
    private val day = LocalDate.of(2026, 1, 15)

    private lateinit var projectDao: FakeProjectDao
    private lateinit var rosterEntryDao: FakeRosterEntryDao
    private lateinit var attendanceEntryDao: FakeAttendanceEntryDao
    private lateinit var extraPayLineDao: FakeExtraPayLineDao
    private lateinit var writer: FakeAttendanceWriter
    private lateinit var viewModel: AttendanceDayViewModel

    private val project = ProjectEntity(
        id = projectId,
        companyId = "company-1",
        name = "Warehouse count",
        startDate = LocalDate.of(2026, 1, 10),
        currency = "USD",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        projectDao = FakeProjectDao()
        rosterEntryDao = FakeRosterEntryDao()
        attendanceEntryDao = FakeAttendanceEntryDao()
        extraPayLineDao = FakeExtraPayLineDao(attendanceEntryDao)
        attendanceEntryDao.extraPayLineDao = extraPayLineDao
        writer = FakeAttendanceWriter(attendanceEntryDao)

        projectDao.seed(project)
        seedRosterClerk("clerk-a", "Alex", 5000)
        seedRosterClerk("clerk-b", "Bea", 6000)
        seedRosterClerk("clerk-c", "Cy", 7000)

        viewModel = buildViewModel(day)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(initialDate: LocalDate) = AttendanceDayViewModel(
        projectId = projectId,
        initialDate = initialDate,
        projectDao = projectDao,
        rosterEntryDao = rosterEntryDao,
        attendanceEntryDao = attendanceEntryDao,
        extraPayLineDao = extraPayLineDao,
        attendanceWriter = writer,
    )

    private fun seedRosterClerk(clerkId: String, name: String, rate: Long) {
        rosterEntryDao.seed(
            RosterEntryEntity(id = "roster-$clerkId", projectId = projectId, clerkId = clerkId, dailyRate = rate),
        )
        rosterEntryDao.setClerkName(clerkId, name)
        attendanceEntryDao.setClerkName(clerkId, name)
    }

    /** Keeps every WhileSubscribed StateFlow hot so the VM's own `rows.value` reads work. */
    private fun TestScope.startCollecting(): List<Job> = listOf(
        viewModel.rows,
        viewModel.unmarkedCount,
        viewModel.isBeforeStartDate,
        viewModel.pendingUnmark,
        viewModel.project,
    ).map { flow -> launch(Dispatchers.Unconfined) { flow.collect {} } }

    private fun rowFor(clerkId: String): AttendanceRowUi? = viewModel.rows.value.find { it.clerkId == clerkId }

    private fun rowsForClerk(clerkId: String) = attendanceEntryDao.all().filter { it.clerkId == clerkId }

    @Test
    fun `unmarked roster clerk has no row and shows Unmarked`() = runTest {
        val jobs = startCollecting()

        assertEquals(3, viewModel.rows.value.size)
        assertEquals(AttendanceState.UNMARKED, rowFor("clerk-a")?.state)
        assertEquals(3, viewModel.unmarkedCount.value)
        assertTrue(attendanceEntryDao.all().isEmpty())

        jobs.forEach { it.cancel() }
    }

    @Test
    fun `tapping present writes a present row snapshotting the current roster rate`() = runTest {
        val jobs = startCollecting()

        viewModel.onEvent(AttendanceDayEvent.PresentTapped("clerk-a"))

        val rows = rowsForClerk("clerk-a")
        assertEquals(1, rows.size)
        assertTrue(rows.single().present)
        assertEquals(5000L, rows.single().rateSnapshot)
        assertEquals(AttendanceState.PRESENT, rowFor("clerk-a")?.state)

        jobs.forEach { it.cancel() }
    }

    @Test
    fun `tapping absent writes a present-false row`() = runTest {
        val jobs = startCollecting()

        viewModel.onEvent(AttendanceDayEvent.AbsentTapped("clerk-a"))

        val rows = rowsForClerk("clerk-a")
        assertEquals(1, rows.size)
        assertFalse(rows.single().present)
        assertEquals(AttendanceState.ABSENT, rowFor("clerk-a")?.state)

        jobs.forEach { it.cancel() }
    }

    @Test
    fun `switching present to absent keeps a single row and flips the flag`() = runTest {
        val jobs = startCollecting()

        viewModel.onEvent(AttendanceDayEvent.PresentTapped("clerk-a"))
        viewModel.onEvent(AttendanceDayEvent.AbsentTapped("clerk-a"))

        val rows = rowsForClerk("clerk-a")
        assertEquals("switching state must not create a second row", 1, rows.size)
        assertFalse(rows.single().present)

        jobs.forEach { it.cancel() }
    }

    @Test
    fun `tapping the selected state again with no extras deletes the row (back to Unmarked)`() = runTest {
        val jobs = startCollecting()

        viewModel.onEvent(AttendanceDayEvent.PresentTapped("clerk-a"))
        assertEquals(1, rowsForClerk("clerk-a").size)

        viewModel.onEvent(AttendanceDayEvent.PresentTapped("clerk-a"))

        assertTrue("unmark with no extras deletes the row", rowsForClerk("clerk-a").isEmpty())
        assertNull(viewModel.pendingUnmark.value)
        assertEquals(AttendanceState.UNMARKED, rowFor("clerk-a")?.state)

        jobs.forEach { it.cancel() }
    }

    @Test
    fun `unmarking a row that has extras asks first and does not delete until confirmed`() = runTest {
        val jobs = startCollecting()
        // Alex present, with an extra line attached to the day's row.
        viewModel.onEvent(AttendanceDayEvent.PresentTapped("clerk-a"))
        val entryId = rowsForClerk("clerk-a").single().id
        extraPayLineDao.seed(ExtraPayLineEntity(id = "x1", attendanceEntryId = entryId, label = "Lunch", amount = 1000))

        // Tap present again = request unmark; extras exist, so it must prompt, not delete.
        viewModel.onEvent(AttendanceDayEvent.PresentTapped("clerk-a"))

        assertNotNull("must prompt before dropping extras", viewModel.pendingUnmark.value)
        assertEquals("clerk-a", viewModel.pendingUnmark.value?.clerkId)
        assertEquals(1, rowsForClerk("clerk-a").size)
        assertEquals(1, extraPayLineDao.all().size)

        jobs.forEach { it.cancel() }
    }

    @Test
    fun `confirming the unmark deletes the row and cascades its extras`() = runTest {
        val jobs = startCollecting()
        viewModel.onEvent(AttendanceDayEvent.PresentTapped("clerk-a"))
        val entryId = rowsForClerk("clerk-a").single().id
        extraPayLineDao.seed(ExtraPayLineEntity(id = "x1", attendanceEntryId = entryId, label = "Lunch", amount = 1000))
        viewModel.onEvent(AttendanceDayEvent.PresentTapped("clerk-a"))

        viewModel.onEvent(AttendanceDayEvent.ConfirmUnmark)

        assertTrue(rowsForClerk("clerk-a").isEmpty())
        assertTrue("extras must cascade with the row", extraPayLineDao.all().isEmpty())
        assertNull(viewModel.pendingUnmark.value)

        jobs.forEach { it.cancel() }
    }

    @Test
    fun `dismissing the unmark keeps the row and its extras`() = runTest {
        val jobs = startCollecting()
        viewModel.onEvent(AttendanceDayEvent.PresentTapped("clerk-a"))
        val entryId = rowsForClerk("clerk-a").single().id
        extraPayLineDao.seed(ExtraPayLineEntity(id = "x1", attendanceEntryId = entryId, label = "Lunch", amount = 1000))
        viewModel.onEvent(AttendanceDayEvent.PresentTapped("clerk-a"))

        viewModel.onEvent(AttendanceDayEvent.DismissUnmark)

        assertNull(viewModel.pendingUnmark.value)
        assertEquals(1, rowsForClerk("clerk-a").size)
        assertEquals(1, extraPayLineDao.all().size)

        jobs.forEach { it.cancel() }
    }

    @Test
    fun `mark all present skips an explicit absent and leaves anyone already marked`() = runTest {
        val jobs = startCollecting()
        viewModel.onEvent(AttendanceDayEvent.AbsentTapped("clerk-b")) // explicit absent

        viewModel.onEvent(AttendanceDayEvent.MarkAllPresent)

        assertEquals(AttendanceState.PRESENT, rowFor("clerk-a")?.state)
        assertEquals("explicit absent must not be overwritten", AttendanceState.ABSENT, rowFor("clerk-b")?.state)
        assertEquals(AttendanceState.PRESENT, rowFor("clerk-c")?.state)
        assertFalse(rowsForClerk("clerk-b").single().present)
        assertEquals(0, viewModel.unmarkedCount.value)

        jobs.forEach { it.cancel() }
    }

    @Test
    fun `editing the roster rate after a mark does not rewrite existing rows but applies to future days`() = runTest {
        val jobs = startCollecting()
        viewModel.onEvent(AttendanceDayEvent.PresentTapped("clerk-a"))
        assertEquals(5000L, rowsForClerk("clerk-a").single().rateSnapshot)

        // Bump the roster rate (future-days-only rule).
        rosterEntryDao.upsert(
            RosterEntryEntity(id = "roster-clerk-a", projectId = projectId, clerkId = "clerk-a", dailyRate = 9000),
        )

        // The already-recorded day keeps its snapshot.
        assertEquals("past day keeps its snapshot", 5000L, rowsForClerk("clerk-a").single().rateSnapshot)

        // A new day snapshots the new rate.
        viewModel.onEvent(AttendanceDayEvent.NextDay)
        viewModel.onEvent(AttendanceDayEvent.PresentTapped("clerk-a"))
        val nextDayRow = attendanceEntryDao.all().single { it.clerkId == "clerk-a" && it.date == day.plusDays(1) }
        assertEquals("future day snapshots the current rate", 9000L, nextDayRow.rateSnapshot)

        jobs.forEach { it.cancel() }
    }

    @Test
    fun `a day-only walk-in appears as a walk-in row with its own snapshot rate`() = runTest {
        // A clerk with an attendance row but no roster row for this project.
        attendanceEntryDao.setClerkName("walk-1", "Dana")
        attendanceEntryDao.seed(
            AttendanceEntryEntity(
                id = "att-walk", projectId = projectId, clerkId = "walk-1", date = day, present = true, rateSnapshot = 4200,
            ),
        )
        val jobs = startCollecting()

        val walkRow = rowFor("walk-1")
        assertNotNull(walkRow)
        assertTrue(walkRow!!.isWalkIn)
        assertEquals(4200L, walkRow.rateMinorUnits)
        assertEquals(AttendanceState.PRESENT, walkRow.state)
        // 3 roster + 1 walk-in.
        assertEquals(4, viewModel.rows.value.size)

        jobs.forEach { it.cancel() }
    }

    @Test
    fun `mark all present only writes roster clerks and never the walk-ins`() = runTest {
        attendanceEntryDao.setClerkName("walk-1", "Dana")
        attendanceEntryDao.seed(
            AttendanceEntryEntity(
                id = "att-walk", projectId = projectId, clerkId = "walk-1", date = day, present = false, rateSnapshot = 4200,
            ),
        )
        val jobs = startCollecting()

        viewModel.onEvent(AttendanceDayEvent.MarkAllPresent)

        // The walk-in's explicit present=false is untouched (it was skipped as an existing row).
        assertFalse(attendanceEntryDao.all().single { it.clerkId == "walk-1" }.present)
        assertEquals(AttendanceState.PRESENT, rowFor("clerk-a")?.state)

        jobs.forEach { it.cancel() }
    }

    @Test
    fun `a date before the project start date raises the backfill warning without blocking`() = runTest {
        viewModel = buildViewModel(LocalDate.of(2026, 1, 5)) // before startDate 2026-01-10
        val jobs = startCollecting()

        assertTrue(viewModel.isBeforeStartDate.value)

        viewModel.onEvent(AttendanceDayEvent.DateSelected(LocalDate.of(2026, 1, 20)))
        assertFalse(viewModel.isBeforeStartDate.value)

        jobs.forEach { it.cancel() }
    }

    @Test
    fun `a rapid present-absent-present toggle settles as exactly one present row`() = runTest {
        val jobs = startCollecting()

        viewModel.onEvent(AttendanceDayEvent.PresentTapped("clerk-a"))
        viewModel.onEvent(AttendanceDayEvent.AbsentTapped("clerk-a"))
        viewModel.onEvent(AttendanceDayEvent.PresentTapped("clerk-a"))

        val rows = rowsForClerk("clerk-a")
        assertEquals("rapid toggle must not duplicate rows", 1, rows.size)
        assertTrue("last tap wins", rows.single().present)

        jobs.forEach { it.cancel() }
    }
}
