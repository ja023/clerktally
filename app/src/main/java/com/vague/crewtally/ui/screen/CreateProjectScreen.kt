package com.vague.crewtally.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vague.crewtally.CrewTallyApplication
import com.vague.crewtally.R
import com.vague.crewtally.data.local.RoomProjectRosterWriter
import com.vague.crewtally.ui.viewmodel.CreateProjectEvent
import com.vague.crewtally.ui.viewmodel.CreateProjectStep
import com.vague.crewtally.ui.viewmodel.CreateProjectViewModel

/**
 * Hosts the three-step create-project wizard behind one top bar. Steps are internal state
 * on [CreateProjectViewModel], not separate nav destinations, so wizard progress and every
 * field the user has typed survives moving back and forth between steps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(navController: NavController, modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as CrewTallyApplication
    val database = application.database
    val viewModel: CreateProjectViewModel = viewModel(
        factory = CreateProjectViewModel.factory(
            projectDao = database.projectDao(),
            companyDao = database.companyDao(),
            clerkDao = database.clerkDao(),
            rosterEntryDao = database.rosterEntryDao(),
            writer = remember { RoomProjectRosterWriter(database) },
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val companies by viewModel.companies.collectAsStateWithLifecycle()
    val roomClerks by viewModel.clerks.collectAsStateWithLifecycle()
    val filteredClerks by viewModel.filteredClerks.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdProjectId) {
        val createdProjectId = state.createdProjectId
        if (createdProjectId != null) {
            navController.navigate("project/$createdProjectId") {
                popUpTo("projects")
            }
            viewModel.onEvent(CreateProjectEvent.SaveHandled)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (state.step) {
                            CreateProjectStep.DETAILS -> stringResource(R.string.create_project_step_details_title)
                            CreateProjectStep.ROSTER -> stringResource(R.string.create_project_step_roster_title)
                            CreateProjectStep.RATES -> stringResource(R.string.create_project_step_rates_title)
                        },
                        // The title is the only signal a step changed — no new screen, no
                        // nav transition TalkBack would otherwise announce on its own.
                        modifier = Modifier.semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.step == CreateProjectStep.DETAILS) {
                                navController.popBackStack()
                            } else {
                                viewModel.onEvent(CreateProjectEvent.StepBack)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (state.step) {
                CreateProjectStep.DETAILS -> CreateProjectDetailsStep(
                    state = state,
                    companies = companies,
                    onEvent = viewModel::onEvent,
                )
                CreateProjectStep.ROSTER -> CreateProjectRosterStep(
                    state = state,
                    clerks = filteredClerks,
                    onEvent = viewModel::onEvent,
                )
                CreateProjectStep.RATES -> CreateProjectRatesStep(
                    state = state,
                    selectedClerks = roomClerks.filter { it.id in state.selectedClerkIds },
                    onEvent = viewModel::onEvent,
                )
            }
        }
    }
}
