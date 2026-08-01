package com.vague.crewtally.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vague.crewtally.R
import com.vague.crewtally.data.local.CompanyEntity
import com.vague.crewtally.ui.components.CrewTallyDateField
import com.vague.crewtally.ui.components.CrewTallyDropdownField
import com.vague.crewtally.ui.components.CrewTallyTextField
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.util.CurrencyCodes
import java.time.LocalDate

/**
 * The project details form fields — name, company, currency, location, notes, start date —
 * shared verbatim by the create-project wizard's step 1 and the edit-project screen
 * (LOCKED Phase 2 decision: same slots, same validation, in both places).
 */
@Composable
fun ProjectDetailsFields(
    name: String,
    onNameChange: (String) -> Unit,
    nameError: Boolean,
    companies: List<CompanyEntity>,
    selectedCompanyId: String?,
    onCompanySelected: (String) -> Unit,
    companyError: Boolean,
    currency: String,
    onCurrencyChange: (String) -> Unit,
    currencyError: Boolean,
    location: String,
    onLocationChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    startDate: LocalDate,
    onStartDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CrewTallyTheme.dimens.screenEdge, vertical = CrewTallyTheme.dimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceLg),
    ) {
        CrewTallyTextField(
            label = stringResource(R.string.project_field_name),
            value = name,
            onValueChange = onNameChange,
            isError = nameError,
            supportingText = if (nameError) stringResource(R.string.project_error_name_required) else null,
        )

        if (companies.isEmpty()) {
            NoCompaniesNotice()
        } else {
            CrewTallyDropdownField(
                label = stringResource(R.string.project_field_company),
                value = companies.find { it.id == selectedCompanyId }?.name.orEmpty(),
                onValueChange = onCompanySelected, // receives the picked option's raw value: a company id
                options = companies.map { it.id },
                optionLabel = { id -> companies.find { it.id == id }?.name.orEmpty() },
                editable = false,
                isError = companyError,
                supportingText = if (companyError) stringResource(R.string.project_error_company_required) else null,
            )
        }

        CrewTallyDropdownField(
            label = stringResource(R.string.project_field_currency),
            value = currency,
            onValueChange = { onCurrencyChange(it.uppercase()) },
            options = CurrencyCodes.COMMON.map { it.code },
            optionLabel = { code -> "$code  ${CurrencyCodes.symbolFor(code)}" },
            editable = true,
            isError = currencyError,
            supportingText = when {
                currencyError -> stringResource(R.string.project_error_currency_required)
                else -> stringResource(R.string.project_currency_hint)
            },
        )

        CrewTallyDateField(
            label = stringResource(R.string.project_field_start_date),
            value = startDate,
            onValueChange = onStartDateChange,
        )

        CrewTallyTextField(
            label = stringResource(R.string.project_field_location),
            value = location,
            onValueChange = onLocationChange,
        )

        CrewTallyTextField(
            label = stringResource(R.string.project_field_notes),
            value = notes,
            onValueChange = onNotesChange,
            singleLine = false,
            minLines = 3,
        )
    }
}

@Composable
private fun NoCompaniesNotice(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.project_field_company)
    val notice = stringResource(R.string.project_no_companies_notice)
    Column(
        // Merged so TalkBack reads the field label and the error notice as one node,
        // matching how a normal field's label + supporting text is announced together.
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$label. $notice" },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = notice,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
