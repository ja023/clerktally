package com.vague.crewtally.ui.viewmodel

import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.data.local.ProjectStatus
import com.vague.crewtally.data.local.RosterEntryEntity
import com.vague.crewtally.testutil.FakeCompanyDao
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
        projectDao.seed(baseProject)
        viewModel = ProjectDetailViewModel("project-1", projectDao, companyDao, rosterEntryDao)
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
}
