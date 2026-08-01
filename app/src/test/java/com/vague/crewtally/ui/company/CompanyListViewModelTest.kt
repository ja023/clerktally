package com.vague.crewtally.ui.company

import com.vague.crewtally.data.local.CompanyEntity
import com.vague.crewtally.testutil.FakeCompanyDao
import com.vague.crewtally.testutil.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompanyListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tocs = CompanyEntity(id = "1", name = "TOCS", contactPerson = "Nadia", archived = false)
    private val retired = CompanyEntity(id = "2", name = "Old Client", archived = true)

    @Test
    fun `active companies are listed and archived companies are hidden by default`() = runTest {
        // Arrange
        val dao = FakeCompanyDao().apply { seed(tocs, retired) }
        val viewModel = CompanyListViewModel(dao)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Act
        val state = viewModel.uiState.value

        // Assert
        assertEquals(listOf("TOCS"), state.activeCompanies.map { it.name })
        assertTrue(state.archivedCompanies.isEmpty())
        job.cancel()
    }

    @Test
    fun `show archived toggle reveals the archived section`() = runTest {
        // Arrange
        val dao = FakeCompanyDao().apply { seed(tocs, retired) }
        val viewModel = CompanyListViewModel(dao)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Act
        viewModel.onEvent(CompanyListEvent.ShowArchivedChanged(true))
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertTrue(state.showArchived)
        assertEquals(listOf("Old Client"), state.archivedCompanies.map { it.name })
        job.cancel()
    }

    @Test
    fun `hasAnyCompanies is false only when the store is genuinely empty`() = runTest {
        // Arrange
        val dao = FakeCompanyDao()
        val viewModel = CompanyListViewModel(dao)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Act
        val emptyState = viewModel.uiState.value
        dao.seed(retired)
        advanceUntilIdle()
        val nonEmptyState = viewModel.uiState.value

        // Assert
        assertFalse(emptyState.hasAnyCompanies)
        assertTrue(nonEmptyState.hasAnyCompanies)
        job.cancel()
    }
}
