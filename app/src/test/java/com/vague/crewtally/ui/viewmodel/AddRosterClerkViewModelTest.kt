package com.vague.crewtally.ui.viewmodel

import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.data.local.ProjectRosterWriter
import com.vague.crewtally.data.local.RosterEntryEntity
import com.vague.crewtally.testutil.FakeClerkDao
import com.vague.crewtally.testutil.FakeProjectDao
import com.vague.crewtally.testutil.FakeProjectRosterWriter
import com.vague.crewtally.testutil.FakeRosterEntryDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Headless JVM tests for [AddRosterClerkViewModel]: the rate-suggestion rule on pick, the
 * atomic re-add-after-removal path that used to be a TOCTOU bug (bare getForPair + upsert
 * split across two DAO calls), and the double-tap re-entrancy guard on save.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddRosterClerkViewModelTest {

    private val projectId = "project-1"

    private lateinit var clerkDao: FakeClerkDao
    private lateinit var rosterEntryDao: FakeRosterEntryDao
    private lateinit var writer: FakeProjectRosterWriter
    private lateinit var viewModel: AddRosterClerkViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        clerkDao = FakeClerkDao()
        rosterEntryDao = FakeRosterEntryDao()
        writer = FakeProjectRosterWriter(FakeProjectDao(), rosterEntryDao)
        viewModel = AddRosterClerkViewModel(projectId, clerkDao, rosterEntryDao, writer)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `picking a clerk with no roster history anywhere prefills a blank rate`() = runTest {
        clerkDao.seed(ClerkEntity(id = "clerk-1", name = "Alex"))

        viewModel.onEvent(AddRosterClerkEvent.ClerkPicked("clerk-1", "Alex"))

        val state = viewModel.state.value
        assertEquals(AddRosterClerkStep.RATE, state.step)
        assertEquals("", state.rateInput)
    }

    @Test
    fun `picking a previously rostered clerk prefills their most recent rate anywhere`() = runTest {
        clerkDao.seed(ClerkEntity(id = "clerk-1", name = "Alex"))
        rosterEntryDao.seed(
            RosterEntryEntity(id = "r1", projectId = "other-project", clerkId = "clerk-1", dailyRate = 4500),
        )

        viewModel.onEvent(AddRosterClerkEvent.ClerkPicked("clerk-1", "Alex"))

        assertEquals("45.00", viewModel.state.value.rateInput)
    }

    @Test
    fun `save on a soft-removed pair reuses the same row id and clears removedAt`() = runTest {
        rosterEntryDao.seed(
            RosterEntryEntity(
                id = "roster-1",
                projectId = projectId,
                clerkId = "clerk-1",
                dailyRate = 3000,
                removedAt = 111_000L,
            ),
        )
        viewModel.onEvent(AddRosterClerkEvent.ClerkPicked("clerk-1", "Alex"))
        viewModel.onEvent(AddRosterClerkEvent.RateChanged("40.00"))

        viewModel.onEvent(AddRosterClerkEvent.Save)

        val rows = rosterEntryDao.all().filter { it.projectId == projectId && it.clerkId == "clerk-1" }
        assertEquals("re-adding must not create a second row for the same pair", 1, rows.size)
        assertEquals("roster-1", rows.single().id)
        assertNull("removedAt must be cleared on re-add", rows.single().removedAt)
        assertEquals(4000L, rows.single().dailyRate)
    }

    @Test
    fun `save on a never-rostered pair creates exactly one new row`() = runTest {
        viewModel.onEvent(AddRosterClerkEvent.ClerkPicked("clerk-2", "Sam"))
        viewModel.onEvent(AddRosterClerkEvent.RateChanged("50"))

        viewModel.onEvent(AddRosterClerkEvent.Save)

        val rows = rosterEntryDao.all().filter { it.projectId == projectId && it.clerkId == "clerk-2" }
        assertEquals(1, rows.size)
        assertEquals(5000L, rows.single().dailyRate)
        assertTrue(viewModel.state.value.saveComplete)
    }

    @Test
    fun `double tap save writes exactly one row`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gatedWriter = GatedProjectRosterWriter(writer, gate)
        val gatedViewModel = AddRosterClerkViewModel(projectId, clerkDao, rosterEntryDao, gatedWriter)
        gatedViewModel.onEvent(AddRosterClerkEvent.ClerkPicked("clerk-1", "Alex"))
        gatedViewModel.onEvent(AddRosterClerkEvent.RateChanged("40"))

        // First tap: runs eagerly (Unconfined) up to the gate, where it genuinely suspends —
        // isSaving stays true while the write is "in flight".
        gatedViewModel.onEvent(AddRosterClerkEvent.Save)
        // Second tap while isSaving is still true: the re-entrancy guard must no-op this.
        gatedViewModel.onEvent(AddRosterClerkEvent.Save)

        gate.complete(Unit)

        assertEquals(1, gatedWriter.callCount)
        val rows = rosterEntryDao.all().filter { it.projectId == projectId && it.clerkId == "clerk-1" }
        assertEquals(1, rows.size)
    }
}

/**
 * Wraps a real [ProjectRosterWriter] but suspends on [gate] before delegating
 * [upsertRosterEntryForPair] — lets a test pause a save mid-flight to exercise the ViewModel's
 * `isSaving` re-entrancy guard against a genuine concurrent second call, which an
 * always-immediately-completing fake can't reproduce under [UnconfinedTestDispatcher].
 */
private class GatedProjectRosterWriter(
    private val delegate: ProjectRosterWriter,
    private val gate: CompletableDeferred<Unit>,
) : ProjectRosterWriter {
    var callCount: Int = 0
        private set

    override suspend fun createProjectWithRoster(project: ProjectEntity, rosterEntries: List<RosterEntryEntity>) {
        delegate.createProjectWithRoster(project, rosterEntries)
    }

    override suspend fun upsertRosterEntryForPair(projectId: String, clerkId: String, dailyRate: Long) {
        callCount += 1
        gate.await()
        delegate.upsertRosterEntryForPair(projectId, clerkId, dailyRate)
    }
}
