package com.vague.crewtally.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vague.crewtally.R
import com.vague.crewtally.data.local.CompanyEntity
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.viewmodel.CreateProjectEvent
import com.vague.crewtally.ui.viewmodel.CreateProjectUiState

/** Create-project step 1: name, company, currency, location, notes, start date. */
@Composable
fun CreateProjectDetailsStep(
    state: CreateProjectUiState,
    companies: List<CompanyEntity>,
    onEvent: (CreateProjectEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ProjectDetailsFields(
            name = state.name,
            onNameChange = { onEvent(CreateProjectEvent.NameChanged(it)) },
            nameError = state.nameError,
            companies = companies,
            selectedCompanyId = state.selectedCompanyId,
            onCompanySelected = { onEvent(CreateProjectEvent.CompanySelected(it)) },
            companyError = state.companyError,
            currency = state.currency,
            onCurrencyChange = { onEvent(CreateProjectEvent.CurrencyChanged(it)) },
            currencyError = state.currencyError,
            location = state.location,
            onLocationChange = { onEvent(CreateProjectEvent.LocationChanged(it)) },
            notes = state.notes,
            onNotesChange = { onEvent(CreateProjectEvent.NotesChanged(it)) },
            startDate = state.startDate,
            onStartDateChange = { onEvent(CreateProjectEvent.StartDateChanged(it)) },
            modifier = Modifier.weight(1f),
        )
        CrewTallyButton(
            text = stringResource(R.string.action_next),
            onClick = { onEvent(CreateProjectEvent.NextFromDetails) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(CrewTallyTheme.dimens.screenEdge),
        )
    }
}
