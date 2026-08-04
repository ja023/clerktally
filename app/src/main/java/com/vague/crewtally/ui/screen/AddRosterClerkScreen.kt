package com.vague.crewtally.ui.screen

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
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.components.CrewTallyListRow
import com.vague.crewtally.ui.components.CrewTallyTextField
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.util.entryFocusFieldLabel
import com.vague.crewtally.ui.util.rememberEntryFocusRequester
import com.vague.crewtally.ui.util.wizardStepTitleSemantics
import com.vague.crewtally.ui.viewmodel.AddRosterClerkEvent
import com.vague.crewtally.ui.viewmodel.AddRosterClerkStep
import com.vague.crewtally.ui.viewmodel.AddRosterClerkViewModel
import com.vague.crewtally.util.CurrencyCodes

/**
 * Adds an existing clerk to a project's roster: pick from active clerks not already on the
 * roster, then set their rate (pre-filled from their most recent rate anywhere).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRosterClerkScreen(
    projectId: String,
    currency: String,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as CrewTallyApplication
    val database = application.database
    val viewModel: AddRosterClerkViewModel = viewModel(
        factory = AddRosterClerkViewModel.factory(
            projectId,
            database.clerkDao(),
            database.rosterEntryDao(),
            application.projectRosterWriter,
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
                        text = if (state.step == AddRosterClerkStep.PICK) {
                            stringResource(R.string.add_clerk_pick_title)
                        } else {
                            stringResource(R.string.add_clerk_rate_title)
                        },
                        // The title is the only signal a step changed — no new screen, no
                        // nav transition TalkBack would otherwise announce on its own. RATE
                        // auto-focuses its rate field below, which races this liveRegion
                        // announcement in TalkBack, so RATE folds the step title into the
                        // focused field's accessibility label instead and skips the liveRegion
                        // here.
                        modifier = Modifier.wizardStepTitleSemantics(
                            announceTitle = state.step != AddRosterClerkStep.RATE,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.step == AddRosterClerkStep.PICK) {
                            navController.popBackStack()
                        } else {
                            viewModel.onEvent(AddRosterClerkEvent.StepBack)
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        when (state.step) {
            AddRosterClerkStep.PICK -> PickClerkStep(
                searchQuery = state.searchQuery,
                availableClerks = availableClerks,
                onSearchChanged = { viewModel.onEvent(AddRosterClerkEvent.SearchChanged(it)) },
                onClerkPicked = { id, name -> viewModel.onEvent(AddRosterClerkEvent.ClerkPicked(id, name)) },
                modifier = Modifier.padding(padding),
            )
            AddRosterClerkStep.RATE -> Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                // Step transition PICK -> RATE moves focus (and the TalkBack cursor) onto the
                // rate field so the user lands ready to type (Phase 6 review-debt paydown). The
                // title's liveRegion is suppressed for this step (see above), so this field's
                // accessibility label carries the step orientation instead.
                val rateFocusRequester = rememberEntryFocusRequester()
                val rateFieldLabel = stringResource(R.string.roster_rate_field_label_for_clerk, state.selectedClerkName)
                CrewTallyTextField(
                    label = rateFieldLabel,
                    accessibilityLabel = entryFocusFieldLabel(
                        stepTitle = stringResource(R.string.add_clerk_rate_title),
                        fieldLabel = rateFieldLabel,
                    ),
                    value = state.rateInput,
                    onValueChange = { viewModel.onEvent(AddRosterClerkEvent.RateChanged(it)) },
                    leadingText = CurrencyCodes.symbolFor(currency),
                    isError = state.rateError,
                    supportingText = if (state.rateError) stringResource(R.string.rates_error_required) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    focusRequester = rateFocusRequester,
                    modifier = Modifier.weight(1f).padding(CrewTallyTheme.dimens.screenEdge),
                )
                CrewTallyButton(
                    text = stringResource(R.string.action_save),
                    onClick = { viewModel.onEvent(AddRosterClerkEvent.Save) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().padding(CrewTallyTheme.dimens.screenEdge),
                )
            }
        }
    }
}

@Composable
private fun PickClerkStep(
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
                    text = stringResource(R.string.add_clerk_none_available),
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
