package com.vague.crewtally.ui.company

import com.vague.crewtally.data.local.CompanyEntity
import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.testutil.FakeCompanyDao
import com.vague.crewtally.testutil.FakeProjectDao
import com.vague.crewtally.testutil.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompanyFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun buildViewModel(
        companyDao: FakeCompanyDao = FakeCompanyDao(),
        projectDao: FakeProjectDao = FakeProjectDao(),
        companyId: String? = null,
    ) = CompanyFormViewModel(companyDao, projectDao, companyId)

    @Test
    fun `new company form starts blank and cannot save`() = runTest {
        // Arrange & Act
        val viewModel = buildViewModel()

        // Assert
        val state = viewModel.uiState.value
        assertEquals("", state.name)
        assertFalse(state.isEditing)
        assertFalse(state.canSave)
    }

    @Test
    fun `save is blocked while name is blank`() = runTest {
        // Arrange
        val companyDao = FakeCompanyDao()
        val viewModel = buildViewModel(companyDao = companyDao)

        // Act
        viewModel.onEvent(CompanyFormEvent.ContactPersonChanged("Nadia"))
        viewModel.onEvent(CompanyFormEvent.Save)

        // Assert
        assertFalse(viewModel.uiState.value.isDone)
        assertTrue(companyDao.all().isEmpty())
    }

    @Test
    fun `save creates a new company once name is filled in`() = runTest {
        // Arrange
        val companyDao = FakeCompanyDao()
        val viewModel = buildViewModel(companyDao = companyDao)

        // Act
        viewModel.onEvent(CompanyFormEvent.NameChanged("TOCS"))
        viewModel.onEvent(CompanyFormEvent.ContactPersonChanged("Nadia"))
        viewModel.onEvent(CompanyFormEvent.PhoneChanged("70999999"))
        viewModel.onEvent(CompanyFormEvent.Save)

        // Assert
        assertTrue(viewModel.uiState.value.isDone)
        val saved = companyDao.all().single()
        assertEquals("TOCS", saved.name)
        assertEquals("Nadia", saved.contactPerson)
        assertFalse(saved.archived)
    }

    @Test
    fun `editing an existing company loads its fields`() = runTest {
        // Arrange
        val companyDao = FakeCompanyDao().apply {
            seed(CompanyEntity(id = "1", name = "TOCS", contactPerson = "Nadia", phone = "70999999", notes = "Beirut"))
        }

        // Act
        val viewModel = buildViewModel(companyDao = companyDao, companyId = "1")

        // Assert
        val state = viewModel.uiState.value
        assertEquals("TOCS", state.name)
        assertEquals("Nadia", state.contactPerson)
        assertEquals("Beirut", state.notes)
        assertTrue(state.isEditing)
    }

    @Test
    fun `archive marks the company archived without deleting it`() = runTest {
        // Arrange
        val companyDao = FakeCompanyDao().apply { seed(CompanyEntity(id = "1", name = "TOCS")) }
        val viewModel = buildViewModel(companyDao = companyDao, companyId = "1")

        // Act
        viewModel.onEvent(CompanyFormEvent.Archive)

        // Assert
        assertTrue(viewModel.uiState.value.isArchived)
        assertEquals(true, companyDao.getById("1")?.archived)
    }

    @Test
    fun `delete is ineligible when the company has projects`() = runTest {
        // Arrange
        val companyDao = FakeCompanyDao().apply { seed(CompanyEntity(id = "1", name = "TOCS")) }
        val projectDao = FakeProjectDao().apply {
            seed(ProjectEntity(id = "p1", companyId = "1", name = "Warehouse Count", startDate = LocalDate.of(2026, 8, 1), currency = "USD"))
        }

        // Act
        val viewModel = buildViewModel(companyDao = companyDao, projectDao = projectDao, companyId = "1")

        // Assert
        assertFalse(viewModel.uiState.value.canDelete)
    }

    @Test
    fun `delete is eligible and removes the company when it has zero projects`() = runTest {
        // Arrange
        val companyDao = FakeCompanyDao().apply { seed(CompanyEntity(id = "1", name = "Typo Co")) }
        val viewModel = buildViewModel(companyDao = companyDao, companyId = "1")

        // Act
        assertTrue(viewModel.uiState.value.canDelete)
        viewModel.onEvent(CompanyFormEvent.Delete)

        // Assert
        assertTrue(viewModel.uiState.value.isDone)
        assertNull(companyDao.getById("1"))
    }

    @Test
    fun `delete rechecks history and aborts if a project appeared after load`() = runTest {
        // Arrange
        val companyDao = FakeCompanyDao().apply { seed(CompanyEntity(id = "1", name = "Typo Co")) }
        val projectDao = FakeProjectDao()
        val viewModel = buildViewModel(companyDao = companyDao, projectDao = projectDao, companyId = "1")
        assertTrue(viewModel.uiState.value.canDelete)

        // Act: a project appears (e.g. created from another screen) after the form loaded
        // but before Delete is tapped.
        projectDao.seed(
            ProjectEntity(id = "p1", companyId = "1", name = "Warehouse Count", startDate = LocalDate.of(2026, 8, 1), currency = "USD"),
        )
        viewModel.onEvent(CompanyFormEvent.Delete)

        // Assert
        assertFalse(viewModel.uiState.value.canDelete)
        assertFalse(viewModel.uiState.value.isDone)
        assertEquals("Typo Co", companyDao.getById("1")?.name)
    }
}
