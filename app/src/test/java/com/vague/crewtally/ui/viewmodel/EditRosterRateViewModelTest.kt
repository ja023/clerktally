package com.vague.crewtally.ui.viewmodel

import com.vague.crewtally.data.local.RosterEntryDao
import com.vague.crewtally.data.local.RosterEntryEntity
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Headless JVM tests for [EditRosterRateViewModel]: save/remove both write onto the SAME
 * [rosterEntryId] the screen opened for (LOCKED — never a second lookup), rate validation,
 * and the double-tap re-entrancy guard shared by save and remove.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditRosterRateViewModelTest {

    private val rosterEntryId = "roster-1"
    private val projectId = "project-1"
    private val clerkId = "clerk-1"
    private val clerkName = "Alex"
    private val initialRateMinorUnits = 3000L

    private lateinit var rosterEntryDao: FakeRosterEntryDao
    private lateinit var viewModel: EditRosterRateViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        rosterEntryDao = FakeRosterEntryDao()
        rosterEntryDao.seed(
            RosterEntryEntity(id = rosterEntryId, projectId = projectId, clerkId = clerkId, dailyRate = initialRateMinorUnits),
        )
        viewModel = EditRosterRateViewModel(
            rosterEntryId,
            projectId,
            clerkId,
            clerkName,
            initialRateMinorUnits,
            rosterEntryDao,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial rate input is prefilled from the existing rate`() {
        assertEquals("30.00", viewModel.state.value.rateInput)
    }

    @Test
    fun `save is blocked when the rate is blank`() = runTest {
        viewModel.onEvent(EditRosterRateEvent.RateChanged(""))

        viewModel.onEvent(EditRosterRateEvent.Save)

        assertTrue(viewModel.state.value.rateError)
        assertEquals(initialRateMinorUnits, rosterEntryDao.getForPair(projectId, clerkId)?.dailyRate)
    }

    @Test
    fun `save is blocked when the rate is zero`() = runTest {
        viewModel.onEvent(EditRosterRateEvent.RateChanged("0"))

        viewModel.onEvent(EditRosterRateEvent.Save)

        assertTrue(viewModel.state.value.rateError)
    }

    @Test
    fun `save writes the new rate onto the same row id`() = runTest {
        viewModel.onEvent(EditRosterRateEvent.RateChanged("45.00"))

        viewModel.onEvent(EditRosterRateEvent.Save)

        val rows = rosterEntryDao.all()
        assertEquals("save must never create a second row", 1, rows.size)
        assertEquals(rosterEntryId, rows.single().id)
        assertEquals(4500L, rows.single().dailyRate)
        assertTrue(viewModel.state.value.saveComplete)
    }

    @Test
    fun `remove stamps removedAt and leaves the rate unchanged`() = runTest {
        viewModel.onEvent(EditRosterRateEvent.Remove)

        val stored = rosterEntryDao.getForPair(projectId, clerkId)
        assertNotNull("removedAt must be stamped", stored?.removedAt)
        assertEquals("removal must not change the rate", initialRateMinorUnits, stored?.dailyRate)
        assertTrue(viewModel.state.value.removeComplete)
    }

    @Test
    fun `double tap save writes exactly one row`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gatedDao = GatedRosterEntryDao(rosterEntryDao, gate)
        val gatedViewModel = EditRosterRateViewModel(
            rosterEntryId,
            projectId,
            clerkId,
            clerkName,
            initialRateMinorUnits,
            gatedDao,
        )
        gatedViewModel.onEvent(EditRosterRateEvent.RateChanged("45.00"))

        // First tap: runs eagerly (Unconfined) up to the gate, where it genuinely suspends —
        // isSaving stays true while the write is "in flight".
        gatedViewModel.onEvent(EditRosterRateEvent.Save)
        // Second tap while isSaving is still true: the re-entrancy guard must no-op this.
        gatedViewModel.onEvent(EditRosterRateEvent.Save)

        gate.complete(Unit)

        assertEquals(1, gatedDao.upsertCallCount)
        assertEquals(1, rosterEntryDao.all().size)
    }

    @Test
    fun `double tap remove stamps removedAt exactly once`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gatedDao = GatedRosterEntryDao(rosterEntryDao, gate)
        val gatedViewModel = EditRosterRateViewModel(
            rosterEntryId,
            projectId,
            clerkId,
            clerkName,
            initialRateMinorUnits,
            gatedDao,
        )

        gatedViewModel.onEvent(EditRosterRateEvent.Remove)
        gatedViewModel.onEvent(EditRosterRateEvent.Remove)

        gate.complete(Unit)

        assertEquals(1, gatedDao.upsertCallCount)
    }
}

/**
 * Wraps a real [RosterEntryDao] but suspends on [gate] before delegating [upsert] — lets a
 * test pause a save/remove mid-flight to exercise the ViewModel's `isSaving` re-entrancy
 * guard against a genuine concurrent second call, which an always-immediately-completing
 * fake can't reproduce under [UnconfinedTestDispatcher].
 */
private class GatedRosterEntryDao(
    private val delegate: RosterEntryDao,
    private val gate: CompletableDeferred<Unit>,
) : RosterEntryDao by delegate {
    var upsertCallCount: Int = 0
        private set

    override suspend fun upsert(entry: RosterEntryEntity) {
        upsertCallCount += 1
        gate.await()
        delegate.upsert(entry)
    }
}
