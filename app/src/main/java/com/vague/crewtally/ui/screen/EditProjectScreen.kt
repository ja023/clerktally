package com.vague.crewtally.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.vague.crewtally.CrewTallyApplication
import com.vague.crewtally.R
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.viewmodel.EditProjectEvent
import com.vague.crewtally.ui.viewmodel.EditProjectViewModel

/** Edits an existing project's details — the same slots and validation as create step 1. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProjectScreen(projectId: String, navController: NavController, modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as CrewTallyApplication
    val database = application.database
    val viewModel: EditProjectViewModel = viewModel(
        factory = EditProjectViewModel.factory(projectId, database.projectDao(), database.companyDao()),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val companies by viewModel.companies.collectAsStateWithLifecycle()

    LaunchedEffect(state.saveComplete) {
        if (state.saveComplete) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.edit_project_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        if (!state.isLoaded) {
            val loadingDescription = stringResource(R.string.cd_loading)
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics { contentDescription = loadingDescription },
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                ProjectDetailsFields(
                    name = state.name,
                    onNameChange = { viewModel.onEvent(EditProjectEvent.NameChanged(it)) },
                    nameError = state.nameError,
                    companies = companies,
                    selectedCompanyId = state.selectedCompanyId,
                    onCompanySelected = { viewModel.onEvent(EditProjectEvent.CompanySelected(it)) },
                    companyError = state.companyError,
                    currency = state.currency,
                    onCurrencyChange = { viewModel.onEvent(EditProjectEvent.CurrencyChanged(it)) },
                    currencyError = state.currencyError,
                    location = state.location,
                    onLocationChange = { viewModel.onEvent(EditProjectEvent.LocationChanged(it)) },
                    notes = state.notes,
                    onNotesChange = { viewModel.onEvent(EditProjectEvent.NotesChanged(it)) },
                    startDate = state.startDate,
                    onStartDateChange = { viewModel.onEvent(EditProjectEvent.StartDateChanged(it)) },
                    modifier = Modifier.weight(1f),
                )
                CrewTallyButton(
                    text = stringResource(R.string.action_save),
                    onClick = { viewModel.onEvent(EditProjectEvent.Save) },
                    enabled = !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CrewTallyTheme.dimens.screenEdge),
                )
            }
        }
    }
}
