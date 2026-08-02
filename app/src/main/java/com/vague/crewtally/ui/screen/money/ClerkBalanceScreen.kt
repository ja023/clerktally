package com.vague.crewtally.ui.screen.money

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.navigation.NavController
import com.vague.crewtally.CrewTallyApplication
import com.vague.crewtally.R
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.screen.report.ReportRoutes
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.theme.CrewTallyType
import com.vague.crewtally.ui.util.owedDisplayText
import com.vague.crewtally.ui.viewmodel.ClerkBalanceViewModel
import com.vague.crewtally.util.CurrencyCodes

/**
 * The per-project clerk balance screen (LOCKED Phase 4): a big derived owed figure (or "Paid in
 * full" / "Advance of X" when the balance is not positive) over the full ledger it breaks down
 * into — present days, extras, and payments. "Record payment" is the single 56dp primary action;
 * a payment row opens its edit form; a secondary "Edit daily rate" action is offered when the
 * clerk still has an active roster row on this project.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClerkBalanceScreen(
    projectId: String,
    clerkId: String,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as CrewTallyApplication
    val database = application.database
    val viewModel: ClerkBalanceViewModel = viewModel(
        factory = ClerkBalanceViewModel.factory(
            projectId = projectId,
            clerkId = clerkId,
            projectDao = database.projectDao(),
            clerkDao = database.clerkDao(),
            rosterEntryDao = database.rosterEntryDao(),
            attendanceEntryDao = database.attendanceEntryDao(),
            extraPayLineDao = database.extraPayLineDao(),
            paymentDao = database.paymentDao(),
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rosterRow by viewModel.rosterRow.collectAsStateWithLifecycle()
    val symbol = CurrencyCodes.symbolFor(state.currency)
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
                OwedFigureCard(owed = state.owed, currencySymbol = symbol)

                CrewTallyButton(
                    text = stringResource(R.string.balance_record_payment),
                    onClick = {
                        navController.navigate(MoneyRoutes.paymentNew(projectId, clerkId, state.clerkName))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                TextButton(
                    onClick = { navController.navigate(ReportRoutes.clerkStatement(projectId, clerkId)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = CrewTallyTheme.dimens.minTarget),
                ) {
                    Text(text = stringResource(R.string.report_share_statement), style = MaterialTheme.typography.labelLarge)
                }

                DaysWorkedSection(
                    presentDays = state.presentDays,
                    earnedTotal = state.earned,
                    currencySymbol = symbol,
                )
                ExtrasLedgerSection(
                    extraLines = state.extraLines,
                    extrasTotal = state.extrasTotal,
                    currencySymbol = symbol,
                )
                PaymentsLedgerSection(
                    payments = state.payments,
                    paidTotal = state.paid,
                    currencySymbol = symbol,
                    onPaymentClick = { payment ->
                        navController.navigate(
                            MoneyRoutes.paymentEdit(projectId, clerkId, payment.id, state.clerkName),
                        )
                    },
                )

                rosterRow?.let { entry ->
                    TextButton(
                        onClick = {
                            val encodedName = Uri.encode(state.clerkName)
                            val encodedCurrency = Uri.encode(state.currency)
                            navController.navigate(
                                "project/$projectId/roster/${entry.id}/edit/$clerkId/" +
                                    "$encodedName/${entry.dailyRate}/$encodedCurrency",
                            )
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = CrewTallyTheme.dimens.minTarget),
                    ) {
                        Text(
                            text = stringResource(R.string.balance_edit_rate),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Spacer(Modifier.height(CrewTallyTheme.dimens.spaceLg))
            }
        }
    }
}

/**
 * The hero balance figure. A live region so a payment recorded on the payment form (which pops
 * back here) announces the new balance; a heading so TalkBack's heading navigation can jump
 * straight to it. The whole card carries one merged description — [owedDisplayText] is already
 * a complete phrase ("Paid in full", "$150.00 owed"), so it is used as-is with no extra label
 * prefix (a red error is never used for this figure — LOCKED #7).
 */
@Composable
private fun OwedFigureCard(owed: Long, currencySymbol: String, modifier: Modifier = Modifier) {
    val display = owedDisplayText(owed, currencySymbol)
    val label = stringResource(R.string.balance_owed_label)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = CrewTallyShape.card,
        tonalElevation = CrewTallyTheme.dimens.elevationCard,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CrewTallyTheme.dimens.spaceXl)
                .semantics(mergeDescendants = true) {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = display
                },
            verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceXs),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = display,
                style = CrewTallyType.moneyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
