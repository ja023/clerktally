package com.vague.crewtally.ui.viewmodel

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

/**
 * Headless tests for [CompanyStatementPickerViewModel]'s [CompanyStatementPickerUiState.isLoading]
 * convention (mirrors [com.vague.crewtally.ui.clerk.ClerkListViewModelTest]'s fake-DAO setup) —
 * the picker screen must show a loading indicator, not a premature "No companies yet." empty
 * state, before Room's first emission.
 */
class CompanyStatementPickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `isLoading is true before Room's first emission`() {
        val dao = FakeCompanyDao()

        val viewModel = CompanyStatementPickerViewModel(dao)

        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.companies.isEmpty())
    }

    @Test
    fun `isLoading clears and companies populate after the first emission`() = runTest {
        val dao = FakeCompanyDao().apply { seed(CompanyEntity(id = "co1", name = "Acme")) }
        val viewModel = CompanyStatementPickerViewModel(dao)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals(listOf("Acme"), state.companies.map { it.name })
        job.cancel()
    }

    @Test
    fun `archived companies are excluded from the picker`() = runTest {
        val dao = FakeCompanyDao().apply {
            seed(
                CompanyEntity(id = "co1", name = "Acme"),
                CompanyEntity(id = "co2", name = "Retired Co", archived = true),
            )
        }
        val viewModel = CompanyStatementPickerViewModel(dao)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals(listOf("Acme"), state.companies.map { it.name })
        job.cancel()
    }
}
