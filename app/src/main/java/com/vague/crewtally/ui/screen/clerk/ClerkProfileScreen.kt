package com.vague.crewtally.ui.screen.clerk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.vague.crewtally.balance.CurrencyTotal
import com.vague.crewtally.report.ClerkProjectBucket
import com.vague.crewtally.ui.components.CrewTallyCurrencyTotalCard
import com.vague.crewtally.ui.components.CrewTallyListRow
import com.vague.crewtally.ui.components.CrewTallyOutlinedButton
import com.vague.crewtally.ui.screen.money.MoneyRoutes
import com.vague.crewtally.ui.screen.report.ReportRoutes
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.theme.CrewTallyType
import com.vague.crewtally.ui.util.owedDisplayText
import com.vague.crewtally.ui.viewmodel.ClerkProfileViewModel
import com.vague.crewtally.ui.viewmodel.ProfileProjectRow
import com.vague.crewtally.util.CurrencyCodes
import com.vague.crewtally.util.Money

/**
 * The clerk profile (Clerks tab → clerk): view-first, with the existing edit form reached from a
 * header Edit action (LOCKED — the profile does not itself edit). Shows totals earned / paid /
 * owed grouped per currency (never summed across currencies) and a per-project row list, each
 * project linking to its balance screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClerkProfileScreen(
    clerkId: String,
    navController: NavController,
    onEditClerk: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as CrewTallyApplication
    val database = application.database
    val viewModel: ClerkProfileViewModel = viewModel(
        factory = ClerkProfileViewModel.factory(
            clerkId = clerkId,
            clerkDao = database.clerkDao(),
            projectDao = database.projectDao(),
            attendanceEntryDao = database.attendanceEntryDao(),
            extraPayLineDao = database.extraPayLineDao(),
            paymentDao = database.paymentDao(),
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loadingDescription = stringResource(R.string.cd_loading)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.clerkName, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onEditClerk(clerkId) }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                },
            )
        },
    ) { padding ->
        if (!state.isLoaded) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loadingDescription })
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(CrewTallyTheme.dimens.screenEdge),
                verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.sectionGap),
            ) {
                if (state.currencyTotals.isEmpty() && state.projectRows.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd)) {
                        Text(
                            text = stringResource(R.string.profile_totals_heading),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.semantics { heading() },
                        )
                        state.currencyTotals.forEach { total -> CurrencyTotalCard(total = total) }
                    }

                    // The two cross-project statements (LOCKED v1.2). They live here, between
                    // the totals they summarise and the per-project rows they expand on, and
                    // only when the clerk actually has history — an empty profile has nothing
                    // to state.
                    Column(verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd)) {
                        CrewTallyOutlinedButton(
                            text = stringResource(R.string.profile_share_active_statement),
                            onClick = {
                                navController.navigate(
                                    ReportRoutes.clerkBucketStatement(clerkId, ClerkProjectBucket.ACTIVE),
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = CrewTallyTheme.dimens.primaryTarget),
                        )
                        CrewTallyOutlinedButton(
                            text = stringResource(R.string.profile_share_history_statement),
                            onClick = {
                                navController.navigate(
                                    ReportRoutes.clerkBucketStatement(clerkId, ClerkProjectBucket.HISTORY),
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = CrewTallyTheme.dimens.primaryTarget),
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd)) {
                        Text(
                            text = stringResource(R.string.profile_projects_heading),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.semantics { heading() },
                        )
                        state.projectRows.forEach { row ->
                            ProfileProjectListRow(
                                row = row,
                                onClick = { navController.navigate(MoneyRoutes.balance(row.projectId, clerkId)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrencyTotalCard(total: CurrencyTotal, modifier: Modifier = Modifier) {
    val symbol = CurrencyCodes.symbolFor(total.currency)
    val earnedText = Money.formatWithSymbol(total.earned, symbol)
    val paidText = Money.formatWithSymbol(total.paid, symbol)
    val owedText = owedDisplayText(total.owed, symbol)
    // Spoken form uses the full currency name ("US Dollar") — the visible label below stays
    // the 3-letter code, which a screen reader would otherwise spell out letter by letter.
    val cardDescription = stringResource(
        R.string.profile_currency_total_description,
        CurrencyCodes.displayNameFor(total.currency),
        earnedText,
        paidText,
        owedText,
    )
    CrewTallyCurrencyTotalCard(
        currencyCode = total.currency,
        earnedLabel = stringResource(R.string.profile_earned_label),
        paidLabel = stringResource(R.string.profile_paid_label),
        owedLabel = stringResource(R.string.profile_owed_label),
        earnedText = earnedText,
        paidText = paidText,
        owedText = owedText,
        contentDescription = cardDescription,
        modifier = modifier,
    )
}

@Composable
private fun ProfileProjectListRow(row: ProfileProjectRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val symbol = CurrencyCodes.symbolFor(row.currency)
    val owedText = owedDisplayText(row.owed, symbol)
    CrewTallyListRow(
        title = row.projectName,
        subtitle = row.currency,
        onClick = onClick,
        contentDescription = stringResource(R.string.profile_project_row_description, row.projectName, owedText),
        trailing = {
            Text(
                text = owedText,
                style = CrewTallyType.moneySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        modifier = modifier,
    )
}
