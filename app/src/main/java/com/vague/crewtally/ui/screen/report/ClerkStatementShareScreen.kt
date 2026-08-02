package com.vague.crewtally.ui.screen.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.vague.crewtally.ui.components.CrewTallyAlertDialog
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.components.CrewTallyCurrencyTotalCard
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.util.owedDisplayText
import com.vague.crewtally.ui.util.shareFile
import com.vague.crewtally.ui.viewmodel.ClerkStatementShareEvent
import com.vague.crewtally.ui.viewmodel.ClerkStatementShareViewModel
import com.vague.crewtally.util.CurrencyCodes
import com.vague.crewtally.util.Money

/**
 * The clerk statement share screen (LOCKED Phase 5): a small full-screen preview of the totals
 * (never the whole PDF), then two big "Share as text" / "Share as PDF" actions that hand the
 * generated file to the system share sheet. Reached from the clerk balance screen's "Share
 * statement" secondary action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClerkStatementShareScreen(
    projectId: String,
    clerkId: String,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val application = context.applicationContext as CrewTallyApplication
    val database = application.database
    val reportStrings = buildReportStrings()
    val viewModel: ClerkStatementShareViewModel = viewModel(
        factory = ClerkStatementShareViewModel.factory(
            projectId = projectId,
            clerkId = clerkId,
            projectDao = database.projectDao(),
            clerkDao = database.clerkDao(),
            companyDao = database.companyDao(),
            attendanceEntryDao = database.attendanceEntryDao(),
            extraPayLineDao = database.extraPayLineDao(),
            paymentDao = database.paymentDao(),
            fileWriter = application.reportFileWriter,
            strings = reportStrings,
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loadingDescription = stringResource(R.string.cd_loading)
    val preparingDescription = stringResource(R.string.report_preparing)

    LaunchedEffect(Unit) {
        viewModel.shareRequests.collect { request -> context.shareFile(request.file, request.mimeType) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_clerk_share_title), modifier = Modifier.semantics { heading() }) },
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
            val statement = state.statement ?: return@Scaffold
            val symbol = CurrencyCodes.symbolFor(statement.currency)

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(CrewTallyTheme.dimens.screenEdge),
                verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.sectionGap),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceXs)) {
                    Text(
                        text = statement.clerkName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "${statement.companyName} - ${statement.projectName}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (statement.dayLines.isEmpty() && statement.extraLines.isEmpty() && statement.paymentLines.isEmpty()) {
                    Text(
                        text = stringResource(R.string.report_clerk_no_activity),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = stringResource(R.string.report_days_worked_label),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = statement.presentDayCount.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    val earnedText = Money.formatWithSymbol(statement.earned + statement.extras, symbol)
                    val paidText = Money.formatWithSymbol(statement.paid, symbol)
                    val owedText = owedDisplayText(statement.owed, symbol)
                    CrewTallyCurrencyTotalCard(
                        currencyCode = statement.currency,
                        earnedLabel = stringResource(R.string.report_earned_label),
                        paidLabel = stringResource(R.string.report_paid_label),
                        owedLabel = stringResource(R.string.report_owed_label),
                        earnedText = earnedText,
                        paidText = paidText,
                        owedText = owedText,
                        contentDescription = stringResource(
                            R.string.report_currency_total_description,
                            CurrencyCodes.displayNameFor(statement.currency),
                            earnedText,
                            paidText,
                            owedText,
                        ),
                    )
                }

                CrewTallyButton(
                    text = stringResource(R.string.report_share_as_text),
                    onClick = { viewModel.onEvent(ClerkStatementShareEvent.ShareAsText) },
                    enabled = !state.isGenerating,
                    modifier = Modifier.fillMaxWidth(),
                )
                CrewTallyButton(
                    text = stringResource(R.string.report_share_as_pdf),
                    onClick = { viewModel.onEvent(ClerkStatementShareEvent.ShareAsPdf) },
                    enabled = !state.isGenerating,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.isGenerating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = preparingDescription
                            },
                    ) {
                        CircularProgressIndicator(modifier = Modifier.height(CrewTallyTheme.dimens.iconLg))
                        Spacer(Modifier.width(CrewTallyTheme.dimens.spaceSm))
                        Text(text = preparingDescription, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    if (state.generationFailure) {
        CrewTallyAlertDialog(
            title = stringResource(R.string.report_generation_error_title),
            body = stringResource(R.string.report_generation_error_body),
            actionLabel = stringResource(R.string.action_ok),
            onDismiss = { viewModel.onEvent(ClerkStatementShareEvent.DismissGenerationFailure) },
        )
    }
}
