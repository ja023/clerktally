package com.vague.crewtally.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.vague.crewtally.R
import com.vague.crewtally.data.local.RosterRowSummary
import com.vague.crewtally.ui.components.CrewTallyListRow
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.theme.CrewTallyType
import com.vague.crewtally.util.Money

/**
 * The project detail screen's roster section: active roster rows with each clerk's daily
 * rate, a header "Add clerk" action, and no attendance entry point (that phase doesn't exist
 * yet — LOCKED scope for this phase — so nothing stands in for it here).
 */
@Composable
fun ProjectRosterSection(
    roster: List<RosterRowSummary>,
    currencySymbol: String,
    onAddClerk: () -> Unit,
    onRowClick: (RosterRowSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(bottom = CrewTallyTheme.dimens.spaceSm),
        ) {
            Text(
                text = stringResource(R.string.project_roster_heading, roster.size),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            TextButton(onClick = onAddClerk) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(CrewTallyTheme.dimens.iconSm),
                )
                Spacer(Modifier.width(CrewTallyTheme.dimens.spaceXs))
                Text(stringResource(R.string.project_roster_add_clerk))
            }
        }

        if (roster.isEmpty()) {
            Text(
                text = stringResource(R.string.project_roster_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd)) {
                roster.forEach { row ->
                    CrewTallyListRow(
                        title = row.clerkName,
                        onClick = { onRowClick(row) },
                        contentDescription = "${row.clerkName}. ${Money.formatWithSymbol(row.entry.dailyRate, currencySymbol)}",
                        trailing = {
                            Text(
                                text = Money.formatWithSymbol(row.entry.dailyRate, currencySymbol),
                                style = CrewTallyType.moneySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                    )
                }
            }
        }
    }
}
