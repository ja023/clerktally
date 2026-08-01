package com.vague.crewtally.ui.viewmodel

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ExtraPayLineEntity
import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.data.local.ProjectStatus
import com.vague.crewtally.data.local.RosterEntryEntity
import com.vague.crewtally.testutil.FakeAttendanceEntryDao
import com.vague.crewtally.testutil.FakeCompanyDao
import com.vague.crewtally.testutil.FakeExtraPayLineDao
import com.vague.crewtally.testutil.FakeProjectDao
import com.vague.crewtally.testutil.FakeRosterEntryDao
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Headless JVM tests for [ProjectDetailViewModel]: mark-completed / reopen status changes,
 * and the roster-remove soft-delete semantics (a removed row keeps its history and simply
 * stops matching the "active roster" filter).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDetailViewModelTest {

    private lateinit var projectDao: FakeProjectDao
    private lateinit var companyDao: FakeCompanyDao
    private lateinit var rosterEntryDao: FakeRosterEntryDao
    private lateinit var attendanceEntryDao: FakeAttendanceEntryDao
    private lateinit var extraPayLineDao: FakeExtraPayLineDao
    private lateinit var viewModel: ProjectDetailViewModel

    private val baseProject = ProjectEntity(
        id = "project-1",
        companyId = "company-1",
        name = "Warehouse count",
        startDate = LocalDate.of(2026, 1, 1),
        currency = "USD",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        projectDao = FakeProjectDao()
        companyDao = FakeCompanyDao()
        rosterEntryDao = FakeRosterEntryDao()
        attendanceEntryDao = FakeAttendanceEntryDao()
        extraPayLineDao = FakeExtraPayLineDao(attendanceEntryDao)
        attendanceEntryDao.extraPayLineDao = extraPayLineDao
        projectDao.seed(baseProject)
        viewModel = ProjectDetailViewModel("project-1", projectDao, companyDao, rosterEntryDao, attendanceEntryDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `mark completed sets status completed and end date to today`() = runTest {
        viewModel.markCompleted(baseProject)

        val updated = projectDao.getById("project-1")
        assertEquals(ProjectStatus.COMPLETED, updated?.status)
        assertEquals(LocalDate.now(), updated?.endDate)
    }

    @Test
    fun `reopen sets status active and clears end date`() = runTest {
        val completed = baseProject.copy(status = ProjectStatus.COMPLETED, endDate = LocalDate.of(2026, 2, 1))
        projectDao.upsert(completed)

        viewModel.reopen(completed)

        val updated = projectDao.getById("project-1")
        assertEquals(ProjectStatus.ACTIVE, updated?.status)
        assertNull(updated?.endDate)
    }

    @Test
    fun `removing a clerk from the roster stamps removedAt and keeps the row`() = runTest {
        val entry = RosterEntryEntity(id = "roster-1", projectId = "project-1", clerkId = "clerk-1", dailyRate = 5000)
        rosterEntryDao.seed(entry)

        viewModel.removeFromRoster(entry)

        val stored = rosterEntryDao.getForPair("project-1", "clerk-1")
        assertNotNull("the row must still exist — soft removal, not delete", stored)
        assertNotNull("removedAt must be stamped", stored?.removedAt)
        assertEquals(5000L, stored?.dailyRate) // rate history is untouched by removal
    }

    @Test
    fun `a removed roster row no longer appears in the active roster`() = runTest {
        val entry = RosterEntryEntity(id = "roster-1", projectId = "project-1", clerkId = "clerk-1", dailyRate = 5000)
        rosterEntryDao.seed(entry)
        rosterEntryDao.setClerkName("clerk-1", "Alex")

        viewModel.removeFromRoster(entry)

        val activeRoster = rosterEntryDao.observeActiveRosterForProject("project-1")
        // Fake DAO backs this Flow with a MutableStateFlow, so .value-equivalent read via
        // first-collected element reflects the latest state synchronously under the
        // UnconfinedTestDispatcher installed above.
        var latest: List<*> = emptyList<Any>()
        val job = launch(Dispatchers.Unconfined) {
            activeRoster.collect { latest = it }
        }
        assertTrue("removed clerk must be excluded from the active roster", latest.isEmpty())
        job.cancel()
    }

    @Test
    fun `attendance day summaries count present rows and sum extras per day newest first`() = runTest {
        val jan15 = LocalDate.of(2026, 1, 15)
        val jan16 = LocalDate.of(2026, 1, 16)
        attendanceEntryDao.seed(
            AttendanceEntryEntity("a1", "project-1", "clerk-1", jan15, present = true, rateSnapshot = 5000),
            AttendanceEntryEntity("a2", "project-1", "clerk-2", jan15, present = false, rateSnapshot = 5000),
            AttendanceEntryEntity("a3", "project-1", "clerk-1", jan16, present = true, rateSnapshot = 5000),
        )
        // Extras: +1000 and a -300 deduction on jan15, nothing on jan16.
        extraPayLineDao.seed(
            ExtraPayLineEntity("x1", "a1", "Lunch", 1000),
            ExtraPayLineEntity("x2", "a1", "Fine", -300),
        )

        var summaries: List<com.vague.crewtally.data.local.AttendanceDaySummary> = emptyList()
        val job = launch(Dispatchers.Unconfined) { viewModel.attendanceDays.collect { summaries = it } }

        assertEquals(2, summaries.size)
        // Newest first.
        assertEquals(jan16, summaries[0].date)
        assertEquals(jan15, summaries[1].date)
        // jan15: one present of two rows, net extras 700.
        assertEquals(1, summaries[1].presentCount)
        assertEquals(700L, summaries[1].extrasTotal)
        // jan16: one present, no extras.
        assertEquals(1, summaries[0].presentCount)
        assertEquals(0L, summaries[0].extrasTotal)

        job.cancel()
    }
}
