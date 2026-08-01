package com.vague.crewtally.ui.screen.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vague.crewtally.CrewTallyApplication
import com.vague.crewtally.R
import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.data.local.RoomAttendanceWriter
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.components.CrewTallyCheckboxRow
import com.vague.crewtally.ui.components.CrewTallyListRow
import com.vague.crewtally.ui.components.CrewTallyTextField
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.viewmodel.AttendanceWalkInEvent
import com.vague.crewtally.ui.viewmodel.AttendanceWalkInStep
import com.vague.crewtally.ui.viewmodel.AttendanceWalkInViewModel
import com.vague.crewtally.util.CurrencyCodes
import java.time.LocalDate

/**
 * Adds a walk-in to one attendance day: pick an active clerk not already on the day, set a rate
 * (pre-filled from their most recent rate anywhere), and optionally also add them to the project
 * roster (default OFF = day-only). Mirrors the roster add flow's two-step shape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceWalkInScreen(
    projectId: String,
    date: LocalDate,
    currency: String,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as CrewTallyApplication
    val database = application.database
    val viewModel: AttendanceWalkInViewModel = viewModel(
        factory = AttendanceWalkInViewModel.factory(
            projectId = projectId,
            date = date,
            clerkDao = database.clerkDao(),
            rosterEntryDao = database.rosterEntryDao(),
            attendanceEntryDao = database.attendanceEntryDao(),
            attendanceWriter = RoomAttendanceWriter(database),
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val availableClerks by viewModel.availableClerks.collectAsStateWithLifecycle()

    LaunchedEffect(state.saveComplete) {
        if (state.saveComplete) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.step == AttendanceWalkInStep.PICK) {
                            stringResource(R.string.walk_in_pick_title)
                        } else {
                            stringResource(R.string.walk_in_rate_title)
                        },
                        modifier = Modifier.semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.step == AttendanceWalkInStep.PICK) {
                            navController.popBackStack()
                        } else {
                            viewModel.onEvent(AttendanceWalkInEvent.StepBack)
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        when (state.step) {
            AttendanceWalkInStep.PICK -> PickWalkInStep(
                searchQuery = state.searchQuery,
                availableClerks = availableClerks,
                onSearchChanged = { viewModel.onEvent(AttendanceWalkInEvent.SearchChanged(it)) },
                onClerkPicked = { id, name -> viewModel.onEvent(AttendanceWalkInEvent.ClerkPicked(id, name)) },
                modifier = Modifier.padding(padding),
            )
            AttendanceWalkInStep.RATE -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(CrewTallyTheme.dimens.screenEdge),
                verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceLg),
            ) {
                CrewTallyTextField(
                    label = stringResource(R.string.roster_rate_field_label_for_clerk, state.selectedClerkName),
                    value = state.rateInput,
                    onValueChange = { viewModel.onEvent(AttendanceWalkInEvent.RateChanged(it)) },
                    leadingText = CurrencyCodes.symbolFor(currency),
                    isError = state.rateError,
                    supportingText = if (state.rateError) stringResource(R.string.rates_error_required) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                CrewTallyCheckboxRow(
                    title = stringResource(R.string.walk_in_also_add_to_roster),
                    checked = state.alsoAddToRoster,
                    onCheckedChange = { viewModel.onEvent(AttendanceWalkInEvent.AlsoAddToRosterChanged(it)) },
                )
                CrewTallyButton(
                    text = stringResource(R.string.action_save),
                    onClick = { viewModel.onEvent(AttendanceWalkInEvent.Save) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PickWalkInStep(
    searchQuery: String,
    availableClerks: List<ClerkEntity>,
    onSearchChanged: (String) -> Unit,
    onClerkPicked: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        CrewTallyTextField(
            label = stringResource(R.string.roster_search_label),
            value = searchQuery,
            onValueChange = onSearchChanged,
            modifier = Modifier.padding(
                horizontal = CrewTallyTheme.dimens.screenEdge,
                vertical = CrewTallyTheme.dimens.spaceMd,
            ),
        )
        if (availableClerks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(CrewTallyTheme.dimens.screenEdge),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.walk_in_none_available),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = CrewTallyTheme.dimens.screenEdge,
                    vertical = CrewTallyTheme.dimens.spaceSm,
                ),
                verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd),
            ) {
                items(availableClerks, key = { it.id }) { clerk ->
                    CrewTallyListRow(
                        title = clerk.name,
                        onClick = { onClerkPicked(clerk.id, clerk.name) },
                    )
                }
            }
        }
    }
}
