package com.vague.crewtally.ui.screen.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vague.crewtally.R
import com.vague.crewtally.ui.components.CrewTallyListRow
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.util.crewTallyDatabase
import com.vague.crewtally.ui.viewmodel.CompanyStatementPickerViewModel

/**
 * The v1.1 company statement picker: non-archived companies only (LOCKED), big senior-friendly
 * rows. Tapping a row lands on the SAME [CompanyReportShareScreen] the company edit form's
 * "Share company report" action reaches (LOCKED: one screen, two entry points).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyStatementPickerScreen(
    onCompanySelected: (String) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: CompanyStatementPickerViewModel = viewModel(
        factory = CompanyStatementPickerViewModel.factory(LocalContext.current.crewTallyDatabase().companyDao()),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loadingDescription = stringResource(R.string.cd_loading)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reports_company_statement), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(modifier = modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loadingDescription })
            }
            state.companies.isEmpty() -> Box(modifier = modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.reports_company_picker_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    horizontal = CrewTallyTheme.dimens.screenEdge,
                    vertical = CrewTallyTheme.dimens.spaceLg,
                ),
                verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd),
            ) {
                items(state.companies, key = { it.id }) { company ->
                    CrewTallyListRow(
                        title = company.name,
                        onClick = { onCompanySelected(company.id) },
                    )
                }
            }
        }
    }
}
