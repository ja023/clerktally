package com.vague.crewtally.ui.viewmodel

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.AttendanceWriter
import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.data.local.RosterEntryEntity
import com.vague.crewtally.testutil.FakeAttendanceEntryDao
import com.vague.crewtally.testutil.FakeAttendanceWriter
import com.vague.crewtally.testutil.FakeClerkDao
import com.vague.crewtally.testutil.FakeExtraPayLineDao
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Headless JVM tests for [AttendanceWalkInViewModel]: the day-only vs roster-join save paths,
 * the pick list excluding clerks already on the roster or already on the day, and the rate
 * pre-fill from a clerk's most recent rate anywhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceWalkInViewModelTest {

    private val projectId = "project-1"
    private val date = LocalDate.of(2026, 1, 15)

    private lateinit var clerkDao: FakeClerkDao
    private lateinit var rosterEntryDao: FakeRosterEntryDao
    private lateinit var attendanceEntryDao: FakeAttendanceEntryDao
    private lateinit var extraPayLineDao: FakeExtraPayLineDao
    private lateinit var writer: FakeAttendanceWriter
    private lateinit var viewModel: AttendanceWalkInViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        clerkDao = FakeClerkDao()
        rosterEntryDao = FakeRosterEntryDao()
        attendanceEntryDao = FakeAttendanceEntryDao()
        extraPayLineDao = FakeExtraPayLineDao(attendanceEntryDao)
        attendanceEntryDao.extraPayLineDao = extraPayLineDao
        writer = FakeAttendanceWriter(attendanceEntryDao, extraPayLineDao, rosterEntryDao)

        clerkDao.seed(
            ClerkEntity(id = "clerk-a", name = "Alex"),
            ClerkEntity(id = "clerk-b", name = "Bea"),
            ClerkEntity(id = "clerk-c", name = "Cy"),
        )
        // clerk-a is on the active roster; clerk-b already has a row on this day.
        rosterEntryDao.seed(
            RosterEntryEntity(id = "r-a", projectId = projectId, clerkId = "clerk-a", dailyRate = 5000),
        )
        attendanceEntryDao.seed(
            AttendanceEntryEntity(
                id = "att-b", projectId = projectId, clerkId = "clerk-b", date = date, present = true, rateSnapshot = 6000,
            ),
        )
        viewModel = buildViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(attendanceWriter: AttendanceWriter = writer) = AttendanceWalkInViewModel(
        projectId = projectId,
        date = date,
        clerkDao = clerkDao,
        rosterEntryDao = rosterEntryDao,
        attendanceEntryDao = attendanceEntryDao,
        attendanceWriter = attendanceWriter,
    )

    private fun TestScope.keepAvailableHot(): Job =
        launch(Dispatchers.Unconfined) { viewModel.availableClerks.collect {} }

    @Test
    fun `pick list excludes roster clerks and clerks already on the day`() = runTest {
        val job = keepAvailableHot()

        val available = viewModel.availableClerks.value
        assertEquals(listOf("clerk-c"), available.map { it.id })

        job.cancel()
    }

    @Test
    fun `picking a clerk prefills their most recent rate anywhere`() = runTest {
        rosterEntryDao.seed(
            RosterEntryEntity(id = "r-old", projectId = "other", clerkId = "clerk-c", dailyRate = 4500),
        )

        viewModel.onEvent(AttendanceWalkInEvent.ClerkPicked("clerk-c", "Cy"))

        assertEquals(AttendanceWalkInStep.RATE, viewModel.state.value.step)
        assertEquals("45.00", viewModel.state.value.rateInput)
    }

    @Test
    fun `a day-only walk-in writes an attendance row but no roster row`() = runTest {
        viewModel.onEvent(AttendanceWalkInEvent.ClerkPicked("clerk-c", "Cy"))
        viewModel.onEvent(AttendanceWalkInEvent.RateChanged("42"))

        viewModel.onEvent(AttendanceWalkInEvent.Save)

        val rows = attendanceEntryDao.all().filter { it.clerkId == "clerk-c" }
        assertEquals(1, rows.size)
        assertTrue("walk-in is present", rows.single().present)
        assertEquals(4200L, rows.single().rateSnapshot)
        assertNull("no roster row for a day-only walk-in", rosterEntryDao.getForPair(projectId, "clerk-c"))
        assertEquals(1, writer.saveWalkInCallCount)
        assertTrue(viewModel.state.value.saveComplete)
    }

    @Test
    fun `a roster-join walk-in writes the attendance row and a roster row`() = runTest {
        viewModel.onEvent(AttendanceWalkInEvent.ClerkPicked("clerk-c", "Cy"))
        viewModel.onEvent(AttendanceWalkInEvent.RateChanged("42"))
        viewModel.onEvent(AttendanceWalkInEvent.AlsoAddToRosterChanged(true))

        viewModel.onEvent(AttendanceWalkInEvent.Save)

        assertEquals(1, attendanceEntryDao.all().count { it.clerkId == "clerk-c" })
        val rosterRow = rosterEntryDao.getForPair(projectId, "clerk-c")
        assertEquals(4200L, rosterRow?.dailyRate)
        assertNull("newly added roster row is active", rosterRow?.removedAt)
        assertEquals(1, writer.saveWalkInCallCount)
    }

    @Test
    fun `an invalid rate blocks the save`() = runTest {
        viewModel.onEvent(AttendanceWalkInEvent.ClerkPicked("clerk-c", "Cy"))
        viewModel.onEvent(AttendanceWalkInEvent.RateChanged(""))

        viewModel.onEvent(AttendanceWalkInEvent.Save)

        assertTrue(viewModel.state.value.rateError)
        assertTrue(attendanceEntryDao.all().none { it.clerkId == "clerk-c" })
        assertEquals(0, writer.setAttendanceCallCount)
    }
}
