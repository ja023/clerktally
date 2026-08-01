package com.vague.crewtally.ui.viewmodel

import com.vague.crewtally.data.local.ProjectStatus
import com.vague.crewtally.data.local.RosterEntryEntity
import com.vague.crewtally.testutil.FakeClerkDao
import com.vague.crewtally.testutil.FakeCompanyDao
import com.vague.crewtally.testutil.FakeProjectDao
import com.vague.crewtally.testutil.FakeProjectRosterWriter
import com.vague.crewtally.testutil.FakeRosterEntryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Headless JVM tests for [CreateProjectViewModel]: create-flow field validation (blank
 * name/company/currency block Next; blank/zero rate blocks Save) and the rate-suggestion
 * rule (most recent roster rate anywhere wins, none found means blank).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateProjectViewModelTest {

    private lateinit var projectDao: FakeProjectDao
    private lateinit var companyDao: FakeCompanyDao
    private lateinit var clerkDao: FakeClerkDao
    private lateinit var rosterEntryDao: FakeRosterEntryDao
    private lateinit var writer: FakeProjectRosterWriter
    private lateinit var viewModel: CreateProjectViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        projectDao = FakeProjectDao()
        companyDao = FakeCompanyDao()
        clerkDao = FakeClerkDao()
        rosterEntryDao = FakeRosterEntryDao()
        writer = FakeProjectRosterWriter(projectDao, rosterEntryDao)
        viewModel = CreateProjectViewModel(projectDao, companyDao, clerkDao, rosterEntryDao, writer)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `next from details blocked when name is blank`() = runTest {
        viewModel.onEvent(CreateProjectEvent.CompanySelected("company-1"))
        viewModel.onEvent(CreateProjectEvent.CurrencyChanged("USD"))

        viewModel.onEvent(CreateProjectEvent.NextFromDetails)

        val state = viewModel.state.value
        assertEquals(CreateProjectStep.DETAILS, state.step)
        assertTrue(state.nameError)
    }

    @Test
    fun `next from details blocked when company not selected`() = runTest {
        viewModel.onEvent(CreateProjectEvent.NameChanged("Warehouse count"))
        viewModel.onEvent(CreateProjectEvent.CurrencyChanged("USD"))

        viewModel.onEvent(CreateProjectEvent.NextFromDetails)

        val state = viewModel.state.value
        assertEquals(CreateProjectStep.DETAILS, state.step)
        assertTrue(state.companyError)
    }

    @Test
    fun `next from details blocked when currency is blank`() = runTest {
        viewModel.onEvent(CreateProjectEvent.NameChanged("Warehouse count"))
        viewModel.onEvent(CreateProjectEvent.CompanySelected("company-1"))

        viewModel.onEvent(CreateProjectEvent.NextFromDetails)

        val state = viewModel.state.value
        assertEquals(CreateProjectStep.DETAILS, state.step)
        assertTrue(state.currencyError)
    }

    @Test
    fun `next from details advances to roster step when all required fields are filled`() = runTest {
        fillValidDetails()

        val state = viewModel.state.value
        assertEquals(CreateProjectStep.ROSTER, state.step)
        assertFalse(state.nameError)
        assertFalse(state.companyError)
        assertFalse(state.currencyError)
    }

    @Test
    fun `zero clerks selected is allowed past the roster step`() = runTest {
        fillValidDetails()

        viewModel.onEvent(CreateProjectEvent.NextFromRoster)

        assertEquals(CreateProjectStep.RATES, viewModel.state.value.step)
        assertTrue(viewModel.state.value.selectedClerkIds.isEmpty())
    }

    @Test
    fun `rate suggestion prefills the clerk's most recent roster rate anywhere`() = runTest {
        // Arrange: clerk had two prior roster rows on other projects — the LAST one inserted
        // (5000 minor units = 50.00) is the "most recent by creation" rate.
        rosterEntryDao.seed(
            RosterEntryEntity(id = "r1", projectId = "old-project-1", clerkId = "clerk-1", dailyRate = 3000),
            RosterEntryEntity(id = "r2", projectId = "old-project-2", clerkId = "clerk-1", dailyRate = 5000),
        )
        fillValidDetails()
        viewModel.onEvent(CreateProjectEvent.ClerkToggled("clerk-1"))

        viewModel.onEvent(CreateProjectEvent.NextFromRoster)

        assertEquals("50.00", viewModel.state.value.rateInputs["clerk-1"])
    }

    @Test
    fun `rate suggestion is blank when the clerk has no roster history`() = runTest {
        fillValidDetails()
        viewModel.onEvent(CreateProjectEvent.ClerkToggled("clerk-never-rostered"))

        viewModel.onEvent(CreateProjectEvent.NextFromRoster)

        assertEquals("", viewModel.state.value.rateInputs["clerk-never-rostered"])
    }

    @Test
    fun `save is blocked when a selected clerk's rate is blank or zero`() = runTest {
        fillValidDetails()
        viewModel.onEvent(CreateProjectEvent.ClerkToggled("clerk-blank"))
        viewModel.onEvent(CreateProjectEvent.ClerkToggled("clerk-zero"))
        viewModel.onEvent(CreateProjectEvent.NextFromRoster)
        viewModel.onEvent(CreateProjectEvent.RateChanged("clerk-zero", "0"))
        // clerk-blank is left untouched — its rateInputs entry stays the prefilled blank.

        viewModel.onEvent(CreateProjectEvent.Save)

        val state = viewModel.state.value
        assertTrue("clerk-blank" in state.rateErrors)
        assertTrue("clerk-zero" in state.rateErrors)
        assertNull(state.createdProjectId)
        assertEquals(0, writer.callCount)
    }

    @Test
    fun `save creates the project and roster in one atomic write when valid`() = runTest {
        fillValidDetails(name = "Warehouse count", companyId = "company-9", currency = "usd")
        viewModel.onEvent(CreateProjectEvent.ClerkToggled("clerk-1"))
        viewModel.onEvent(CreateProjectEvent.ClerkToggled("clerk-2"))
        viewModel.onEvent(CreateProjectEvent.NextFromRoster)
        viewModel.onEvent(CreateProjectEvent.RateChanged("clerk-1", "40"))
        viewModel.onEvent(CreateProjectEvent.RateChanged("clerk-2", "35.50"))

        viewModel.onEvent(CreateProjectEvent.Save)

        val state = viewModel.state.value
        assertNotNull(state.createdProjectId)
        assertEquals(1, writer.callCount)

        val savedProject = requireNotNull(writer.lastProject)
        assertEquals("Warehouse count", savedProject.name)
        assertEquals("company-9", savedProject.companyId)
        assertEquals("USD", savedProject.currency) // currency is normalized to uppercase
        assertEquals(ProjectStatus.ACTIVE, savedProject.status)

        val savedRoster = writer.lastRosterEntries.associateBy { it.clerkId }
        assertEquals(4000L, savedRoster.getValue("clerk-1").dailyRate)
        assertEquals(3550L, savedRoster.getValue("clerk-2").dailyRate)
    }

    private suspend fun fillValidDetails(
        name: String = "Warehouse count",
        companyId: String = "company-1",
        currency: String = "USD",
    ) {
        viewModel.onEvent(CreateProjectEvent.NameChanged(name))
        viewModel.onEvent(CreateProjectEvent.CompanySelected(companyId))
        viewModel.onEvent(CreateProjectEvent.CurrencyChanged(currency))
        viewModel.onEvent(CreateProjectEvent.NextFromDetails)
    }
}
