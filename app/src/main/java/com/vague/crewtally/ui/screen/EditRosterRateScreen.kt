package com.vague.crewtally.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vague.crewtally.CrewTallyApplication
import com.vague.crewtally.R
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.components.CrewTallyTextField
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.viewmodel.EditRosterRateEvent
import com.vague.crewtally.ui.viewmodel.EditRosterRateViewModel
import com.vague.crewtally.util.CurrencyCodes

/**
 * Edits one clerk's daily rate on a project's roster, or removes them from it. The rate
 * field carries a hint that this only affects FUTURE attendance days (LOCKED data model —
 * past days already snapshotted their rate and stay exactly what they were).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRosterRateScreen(
    rosterEntryId: String,
    projectId: String,
    clerkId: String,
    clerkName: String,
    rateMinorUnits: Long,
    currency: String,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as CrewTallyApplication
    val database = application.database
    val viewModel: EditRosterRateViewModel = viewModel(
        factory = EditRosterRateViewModel.factory(
            rosterEntryId,
            projectId,
            clerkId,
            clerkName,
            rateMinorUnits,
            database.rosterEntryDao(),
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showRemoveConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.saveComplete, state.removeComplete) {
        if (state.saveComplete || state.removeComplete) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(clerkName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(CrewTallyTheme.dimens.screenEdge),
        ) {
            CrewTallyTextField(
                label = stringResource(R.string.roster_rate_field_label),
                value = state.rateInput,
                onValueChange = { viewModel.onEvent(EditRosterRateEvent.RateChanged(it)) },
                leadingText = CurrencyCodes.symbolFor(currency),
                isError = state.rateError,
                supportingText = if (state.rateError) {
                    stringResource(R.string.rates_error_required)
                } else {
                    stringResource(R.string.roster_rate_edit_hint)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            Spacer(Modifier.weight(1f))

            CrewTallyButton(
                text = stringResource(R.string.action_save),
                onClick = { viewModel.onEvent(EditRosterRateEvent.Save) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(CrewTallyTheme.dimens.spaceMd))
            TextButton(onClick = { showRemoveConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.roster_remove_clerk),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text(stringResource(R.string.roster_remove_confirm_title)) },
            text = { Text(stringResource(R.string.roster_remove_confirm_body, clerkName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onEvent(EditRosterRateEvent.Remove)
                    showRemoveConfirm = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
