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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.vague.crewtally.R
import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.components.CrewTallyCheckboxRow
import com.vague.crewtally.ui.components.CrewTallyTextField
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.viewmodel.CreateProjectEvent
import com.vague.crewtally.ui.viewmodel.CreateProjectUiState

/**
 * Create-project step 2: multi-select the roster from active clerks, searchable by name.
 * Zero clerks selected is a valid state (LOCKED Phase 2 decision) — Next is never blocked.
 */
@Composable
fun CreateProjectRosterStep(
    state: CreateProjectUiState,
    clerks: List<ClerkEntity>,
    onEvent: (CreateProjectEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        CrewTallyTextField(
            label = stringResource(R.string.roster_search_label),
            value = state.clerkSearchQuery,
            onValueChange = { onEvent(CreateProjectEvent.ClerkSearchChanged(it)) },
            modifier = Modifier.padding(
                horizontal = CrewTallyTheme.dimens.screenEdge,
                vertical = CrewTallyTheme.dimens.spaceMd,
            ),
        )

        if (clerks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(CrewTallyTheme.dimens.screenEdge),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.roster_no_clerks),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    horizontal = CrewTallyTheme.dimens.screenEdge,
                    vertical = CrewTallyTheme.dimens.spaceSm,
                ),
                verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd),
            ) {
                items(clerks, key = { it.id }) { clerk ->
                    CrewTallyCheckboxRow(
                        title = clerk.name,
                        checked = clerk.id in state.selectedClerkIds,
                        onCheckedChange = { onEvent(CreateProjectEvent.ClerkToggled(clerk.id)) },
                    )
                }
            }
        }

        Text(
            text = pluralStringResource(
                R.plurals.roster_selected_count,
                state.selectedClerkIds.size,
                state.selectedClerkIds.size,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = CrewTallyTheme.dimens.screenEdge)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        CrewTallyButton(
            text = stringResource(R.string.action_next),
            onClick = { onEvent(CreateProjectEvent.NextFromRoster) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(CrewTallyTheme.dimens.screenEdge),
        )
    }
}
