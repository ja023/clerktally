package com.vague.crewtally.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vague.crewtally.CrewTallyApplication
import com.vague.crewtally.R
import com.vague.crewtally.balance.OutstandingTotal
import com.vague.crewtally.data.local.ProjectSummary
import com.vague.crewtally.ui.components.CrewTallyBrandMark
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.components.CrewTallyClerkProfileButton
import com.vague.crewtally.ui.components.CrewTallyEmptyState
import com.vague.crewtally.ui.components.CrewTallyListRow
import com.vague.crewtally.ui.screen.attendance.AttendanceRoutes
import com.vague.crewtally.ui.screen.money.MoneyRoutes
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.theme.CrewTallyType
import com.vague.crewtally.ui.viewmodel.HomeViewModel
import com.vague.crewtally.ui.viewmodel.OwedClerkGroup
import com.vague.crewtally.ui.viewmodel.OwedClerkRow
import com.vague.crewtally.util.CurrencyCodes
import com.vague.crewtally.util.Money
import java.time.LocalDate

/**
 * The Home tab — a projects-first dashboard (LOCKED Phase 4). Active projects sit on top, each
 * with a straight-to-today "Take attendance" shortcut; then the outstanding total per currency
 * (one big number each, never summed across currencies); then the owed-clerks list sorted by
 * amount, each opening that clerk's balance screen. Every section has its own empty state.
 */
@Composable
fun HomeScreen(navController: NavController, modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as CrewTallyApplication
    val database = application.database
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            projectDao = database.projectDao(),
            clerkDao = database.clerkDao(),
            attendanceEntryDao = database.attendanceEntryDao(),
            extraPayLineDao = database.extraPayLineDao(),
            paymentDao = database.paymentDao(),
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loadingDescription = stringResource(R.string.cd_loading)

    Column(modifier = modifier.fillMaxSize()) {
        // The brand mark sits beside the app name (LOCKED v1.2 "Home tab header"). The heading
        // semantics live on the Row so TalkBack announces one heading, "CrewTally", rather than
        // a decorative image followed by a separate text node.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceSm),
            modifier = Modifier
                .padding(horizontal = CrewTallyTheme.dimens.screenEdge, vertical = CrewTallyTheme.dimens.spaceLg)
                .semantics(mergeDescendants = true) { heading() },
        ) {
            CrewTallyBrandMark()
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // Gate on the first Room emission so the empty-state copy can't flash before real data
        // arrives (mirrors the balance and profile screens' isLoaded gating).
        if (!state.isLoaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loadingDescription })
            }
            return@Column
        }

        if (state.activeProjects.isEmpty() && state.owedClerkGroups.isEmpty() && state.outstanding.isEmpty()) {
            CrewTallyEmptyState(
                title = stringResource(R.string.home_no_projects_title),
                body = stringResource(R.string.home_no_projects_body),
                ctaLabel = stringResource(R.string.home_no_projects_cta),
                onCtaClick = { navController.navigate("project/create") },
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = CrewTallyTheme.dimens.screenEdge,
                vertical = CrewTallyTheme.dimens.spaceMd,
            ),
            verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd),
        ) {
            item(key = "active-projects-heading") {
                SectionHeading(stringResource(R.string.home_active_projects_heading))
            }
            if (state.activeProjects.isEmpty()) {
                item(key = "active-projects-empty") {
                    SectionEmptyLine(stringResource(R.string.home_no_active_projects))
                }
            } else {
                items(state.activeProjects, key = { "project-${it.project.id}" }) { summary ->
                    ActiveProjectCard(
                        summary = summary,
                        onTakeAttendance = {
                            navController.navigate(AttendanceRoutes.day(summary.project.id, LocalDate.now()))
                        },
                    )
                }
            }

            item(key = "outstanding-heading") {
                SectionHeading(stringResource(R.string.home_outstanding_heading))
            }
            if (state.outstanding.isEmpty()) {
                item(key = "outstanding-empty") {
                    SectionEmptyLine(stringResource(R.string.home_all_settled))
                }
            } else {
                items(state.outstanding, key = { "currency-${it.currency}" }) { total ->
                    OutstandingCard(total = total)
                }
            }

            item(key = "owed-heading") {
                SectionHeading(stringResource(R.string.home_owed_heading))
            }
            if (state.owedClerkGroups.isEmpty()) {
                item(key = "owed-empty") {
                    SectionEmptyLine(stringResource(R.string.home_all_settled))
                }
            } else {
                // One subheader per currency (same order as the outstanding totals above),
                // clerks sorted by amount owed descending WITHIN that currency only — comparing
                // raw amounts across currencies would be meaningless (LOCKED).
                state.owedClerkGroups.forEach { group ->
                    item(key = "owed-currency-${group.currency}") {
                        CurrencySubheading(group.currency)
                    }
                    items(group.clerks, key = { "owed-${it.projectId}-${it.clerkId}" }) { row ->
                        OwedClerkListRow(
                            row = row,
                            onClick = { navController.navigate(MoneyRoutes.balance(row.projectId, row.clerkId)) },
                            onOpenProfile = { navController.navigate(MoneyRoutes.profile(row.clerkId)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .padding(top = CrewTallyTheme.dimens.spaceSm)
            .semantics { heading() },
    )
}

@Composable
private fun SectionEmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A currency-code subheader grouping one currency's owed-clerks rows underneath it. */
@Composable
private fun CurrencySubheading(currency: String) {
    Text(
        text = currency,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = CrewTallyTheme.dimens.spaceXs)
            .semantics { heading() },
    )
}

@Composable
private fun ActiveProjectCard(
    summary: ProjectSummary,
    onTakeAttendance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = CrewTallyShape.card,
        tonalElevation = CrewTallyTheme.dimens.elevationCard,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(CrewTallyTheme.dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (summary.companyName.isBlank()) {
                            summary.project.name
                        } else {
                            "${summary.project.name}. ${summary.companyName}"
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceXxs),
            ) {
                Text(
                    text = summary.project.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                if (summary.companyName.isNotBlank()) {
                    Text(
                        text = summary.companyName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            CrewTallyButton(
                text = stringResource(R.string.project_take_attendance),
                onClick = onTakeAttendance,
                contentDescription = stringResource(R.string.project_take_attendance_for, summary.project.name),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OutstandingCard(total: OutstandingTotal, modifier: Modifier = Modifier) {
    val amountText = Money.formatWithSymbol(total.amount, CurrencyCodes.symbolFor(total.currency))
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = CrewTallyShape.card,
        tonalElevation = CrewTallyTheme.dimens.elevationCard,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CrewTallyTheme.dimens.spaceLg)
                .semantics(mergeDescendants = true) {
                    // Spoken form uses the full currency name ("US Dollar") — the visible label
                    // stays the 3-letter code, which a screen reader would otherwise spell out.
                    contentDescription = "${CurrencyCodes.displayNameFor(total.currency)}. $amountText"
                },
            verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceXs),
        ) {
            Text(
                text = total.currency,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = amountText,
                style = CrewTallyType.moneyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * One owed-clerk row. The row itself still opens that clerk's per-project balance (tap target
 * unchanged — LOCKED v1.2); the trailing document button beside it opens the clerk profile,
 * where the two cross-project statements live.
 */
@Composable
private fun OwedClerkListRow(
    row: OwedClerkRow,
    onClick: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val amountText = Money.formatWithSymbol(row.owed, CurrencyCodes.symbolFor(row.currency))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceSm),
        modifier = modifier.fillMaxWidth(),
    ) {
        CrewTallyListRow(
            title = row.clerkName,
            subtitle = row.projectName,
            onClick = onClick,
            contentDescription = stringResource(
                R.string.home_owed_row_description,
                row.clerkName,
                row.projectName,
                amountText,
            ),
            trailing = {
                Text(
                    text = amountText,
                    style = CrewTallyType.moneyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            modifier = Modifier.weight(1f),
        )
        CrewTallyClerkProfileButton(clerkName = row.clerkName, onClick = onOpenProfile)
    }
}
