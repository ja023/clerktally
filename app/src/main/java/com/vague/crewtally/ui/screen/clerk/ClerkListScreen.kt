package com.vague.crewtally.ui.screen.clerk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vague.crewtally.R
import com.vague.crewtally.ui.clerk.ClerkListEvent
import com.vague.crewtally.ui.clerk.ClerkListViewModel
import com.vague.crewtally.ui.clerk.ClerkRow
import com.vague.crewtally.ui.components.CrewTallyArchivedToggle
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.components.CrewTallyEmptyState
import com.vague.crewtally.ui.components.CrewTallyListRow
import com.vague.crewtally.ui.components.CrewTallyTextField
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.util.crewTallyDatabase

/**
 * The Clerks tab: an alphabetical active-clerk list (Room already sorts it), a search box
 * filtering by name/phone, an opt-in archived section, and a persistent add button.
 *
 * Add-button pattern: a full-width [CrewTallyButton] pinned below the list — not a FAB.
 * Phase 0 already established [CrewTallyButton] as "the pattern-setter for every action
 * later phases add", and a floating button is exactly the kind of easy-to-miss/occlude-the-
 * last-row affordance the design system's "no hidden gestures, obvious navigation" rule
 * (see CLAUDE.md) argues against for the ~55-year-old primary user. The Companies list
 * (also Phase 1) reuses the identical pattern.
 */
@Composable
fun ClerkListScreen(
    onAddClerk: () -> Unit,
    onEditClerk: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClerkListViewModel = viewModel(
        factory = ClerkListViewModel.factory(LocalContext.current.crewTallyDatabase().clerkDao()),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.nav_clerks),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(
                    horizontal = CrewTallyTheme.dimens.screenEdge,
                    vertical = CrewTallyTheme.dimens.spaceLg,
                )
                .semantics { heading() },
        )

        val searchLabel = stringResource(R.string.clerks_search_placeholder)
        CrewTallyTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.onEvent(ClerkListEvent.SearchQueryChanged(it)) },
            placeholder = searchLabel,
            leadingIcon = Icons.Filled.Search,
            modifier = Modifier
                .padding(horizontal = CrewTallyTheme.dimens.screenEdge)
                .semantics(mergeDescendants = true) { contentDescription = searchLabel },
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                uiState.isLoading -> Unit
                !uiState.hasAnyClerks -> CrewTallyEmptyState(
                    title = stringResource(R.string.clerks_empty_title),
                    body = stringResource(R.string.clerks_empty_body),
                    ctaLabel = stringResource(R.string.clerks_add_clerk),
                    onCtaClick = onAddClerk,
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
                    if (uiState.activeClerks.isEmpty() && uiState.searchQuery.isNotBlank()) {
                        item {
                            Text(
                                text = stringResource(R.string.clerks_no_matches),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }
                    items(uiState.activeClerks, key = { it.id }) { clerk ->
                        ClerkRowItem(clerk = clerk, onClick = { onEditClerk(clerk.id) })
                    }

                    item {
                        CrewTallyArchivedToggle(
                            label = stringResource(R.string.action_show_archived),
                            checked = uiState.showArchived,
                            onCheckedChange = { viewModel.onEvent(ClerkListEvent.ShowArchivedChanged(it)) },
                        )
                    }

                    if (uiState.showArchived && uiState.archivedClerks.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.clerks_archived_section),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(uiState.archivedClerks, key = { it.id }) { clerk ->
                            ClerkRowItem(clerk = clerk, isArchived = true, onClick = { onEditClerk(clerk.id) })
                        }
                    }
                }
            }
        }

        CrewTallyButton(
            text = stringResource(R.string.clerks_add_clerk),
            onClick = onAddClerk,
            modifier = Modifier
                .fillMaxWidth()
                .padding(CrewTallyTheme.dimens.screenEdge),
        )
    }
}

@Composable
private fun ClerkRowItem(clerk: ClerkRow, onClick: () -> Unit, isArchived: Boolean = false) {
    CrewTallyListRow(
        title = clerk.name,
        subtitle = clerk.phone.ifBlank { null },
        onClick = onClick,
        contentDescription = if (isArchived) {
            stringResource(R.string.cd_archived_record, clerk.name)
        } else {
            clerk.name
        },
    )
}
