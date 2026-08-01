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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.vague.crewtally.R
import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.components.CrewTallyTextField
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.viewmodel.CreateProjectEvent
import com.vague.crewtally.ui.viewmodel.CreateProjectUiState
import com.vague.crewtally.util.CurrencyCodes

/**
 * Create-project step 3: one rate field per selected clerk, pre-filled from
 * [com.vague.crewtally.data.local.RosterEntryDao.getMostRecentRateForClerk]. A blank or
 * zero rate blocks Save (LOCKED Phase 2 decision).
 */
@Composable
fun CreateProjectRatesStep(
    state: CreateProjectUiState,
    selectedClerks: List<ClerkEntity>,
    onEvent: (CreateProjectEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val symbol = remember(state.currency) { CurrencyCodes.symbolFor(state.currency) }

    Column(modifier = modifier.fillMaxSize()) {
        if (selectedClerks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(CrewTallyTheme.dimens.screenEdge),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.rates_no_clerks_notice),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(CrewTallyTheme.dimens.screenEdge),
                verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceLg),
            ) {
                items(selectedClerks, key = { it.id }) { clerk ->
                    val hasError = clerk.id in state.rateErrors
                    CrewTallyTextField(
                        label = clerk.name,
                        value = state.rateInputs[clerk.id].orEmpty(),
                        onValueChange = { onEvent(CreateProjectEvent.RateChanged(clerk.id, it)) },
                        leadingText = symbol,
                        isError = hasError,
                        supportingText = if (hasError) stringResource(R.string.rates_error_required) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        }
        CrewTallyButton(
            text = stringResource(R.string.action_save_project),
            onClick = { onEvent(CreateProjectEvent.Save) },
            enabled = !state.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(CrewTallyTheme.dimens.screenEdge),
        )
    }
}
