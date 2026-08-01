package com.vague.crewtally.ui.screen.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vague.crewtally.R
import com.vague.crewtally.ui.company.CompanyListEvent
import com.vague.crewtally.ui.company.CompanyListViewModel
import com.vague.crewtally.ui.company.CompanyRow
import com.vague.crewtally.ui.components.CrewTallyArchivedToggle
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.components.CrewTallyEmptyState
import com.vague.crewtally.ui.components.CrewTallyListRow
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.util.crewTallyDatabase

/**
 * The Companies list, reached from More. Same active/archived + persistent add-button
 * pattern as [com.vague.crewtally.ui.screen.clerk.ClerkListScreen] — but no search box:
 * Phase 1 LOCKED decisions call companies few enough that scanning the list is enough.
 */
@Composable
fun CompanyListScreen(
    onAddCompany: () -> Unit,
    onEditCompany: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompanyListViewModel = viewModel(
        factory = CompanyListViewModel.factory(LocalContext.current.crewTallyDatabase().companyDao()),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.more_companies),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(
                    horizontal = CrewTallyTheme.dimens.screenEdge,
                    vertical = CrewTallyTheme.dimens.spaceLg,
                )
                .semantics { heading() },
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                uiState.isLoading -> Unit
                !uiState.hasAnyCompanies -> CrewTallyEmptyState(
                    title = stringResource(R.string.companies_empty_title),
                    body = stringResource(R.string.companies_empty_body),
                    ctaLabel = stringResource(R.string.companies_add_company),
                    onCtaClick = onAddCompany,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = CrewTallyTheme.dimens.screenEdge,
                        vertical = CrewTallyTheme.dimens.spaceMd,
                    ),
                    verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceSm),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(uiState.activeCompanies, key = { it.id }) { company ->
                        CompanyRowItem(company = company, onClick = { onEditCompany(company.id) })
                    }

                    item {
                        CrewTallyArchivedToggle(
                            label = stringResource(R.string.action_show_archived),
                            checked = uiState.showArchived,
                            onCheckedChange = { viewModel.onEvent(CompanyListEvent.ShowArchivedChanged(it)) },
                        )
                    }

                    if (uiState.showArchived && uiState.archivedCompanies.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.companies_archived_section),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(uiState.archivedCompanies, key = { it.id }) { company ->
                            CompanyRowItem(company = company, isArchived = true, onClick = { onEditCompany(company.id) })
                        }
                    }
                }
            }
        }

        CrewTallyButton(
            text = stringResource(R.string.companies_add_company),
            onClick = onAddCompany,
            modifier = Modifier
                .fillMaxWidth()
                .padding(CrewTallyTheme.dimens.screenEdge),
        )
    }
}

@Composable
private fun CompanyRowItem(company: CompanyRow, onClick: () -> Unit, isArchived: Boolean = false) {
    CrewTallyListRow(
        title = company.name,
        subtitle = company.contactPerson.ifBlank { null },
        onClick = onClick,
        contentDescription = if (isArchived) {
            stringResource(R.string.cd_archived_record, company.name)
        } else {
            company.name
        },
    )
}
