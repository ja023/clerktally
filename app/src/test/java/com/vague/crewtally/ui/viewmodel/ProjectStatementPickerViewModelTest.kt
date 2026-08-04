package com.vague.crewtally.ui.viewmodel

import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.testutil.FakeProjectDao
import com.vague.crewtally.testutil.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Headless tests for [ProjectStatementPickerViewModel]'s [ProjectStatementPickerUiState.isLoading]
 * convention (mirrors [com.vague.crewtally.ui.clerk.ClerkListViewModelTest]'s fake-DAO setup) —
 * the picker screen must show a loading indicator, not a premature "No projects yet." empty state,
 * before Room's first emission.
 */
class ProjectStatementPickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val jan1 = LocalDate.of(2026, 1, 1)

    @Test
    fun `isLoading is true before Room's first emission`() {
        val dao = FakeProjectDao()

        val viewModel = ProjectStatementPickerViewModel(dao)

        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.projects.isEmpty())
    }

    @Test
    fun `isLoading clears and projects populate after the first emission`() = runTest {
        val dao = FakeProjectDao().apply {
            seed(ProjectEntity(id = "p1", companyId = "co1", name = "Warehouse", startDate = jan1, currency = "USD"))
        }
        val viewModel = ProjectStatementPickerViewModel(dao)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals(listOf("Warehouse"), state.projects.map { it.project.name })
        job.cancel()
    }

    @Test
    fun `a genuinely empty store clears isLoading with an empty project list`() = runTest {
        val dao = FakeProjectDao()
        val viewModel = ProjectStatementPickerViewModel(dao)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.projects.isEmpty())
        job.cancel()
    }
}
