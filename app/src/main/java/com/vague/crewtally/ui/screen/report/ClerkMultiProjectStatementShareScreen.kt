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
import androidx.compose.ui.res.pluralStringResource
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
import com.vague.crewtally.report.ClerkProjectBucket
import com.vague.crewtally.ui.components.CrewTallyAlertDialog
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.components.CrewTallyCurrencyTotalCard
import com.vague.crewtally.ui.components.CrewTallyInlineError
import com.vague.crewtally.ui.components.CrewTallyReportRangeSelector
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.util.owedDisplayText
import com.vague.crewtally.ui.util.shareButtonDisabledDescription
import com.vague.crewtally.ui.util.shareFile
import com.vague.crewtally.ui.viewmodel.ClerkMultiProjectStatementShareEvent
import com.vague.crewtally.ui.viewmodel.ClerkMultiProjectStatementShareUiState
import com.vague.crewtally.ui.viewmodel.ClerkMultiProjectStatementShareViewModel
import com.vague.crewtally.util.CurrencyCodes
import com.vague.crewtally.util.Money

/**
 * The v1.2 cross-project clerk statement share screen, used for BOTH buckets (Active / History
 * — [bucket] decides the title, the empty wording, and the generated file). Preview shows the
 * project count and one total card per currency (never the whole document — the per-project
 * sections live in the generated file), then the two big share actions.
 *
 * When the bucket is empty there is nothing to share, so both actions are disabled and the
 * screen states why (LOCKED v1.2: "empty case shows a clear empty message; nothing to share").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClerkMultiProjectStatementShareScreen(
    clerkId: String,
    bucket: ClerkProjectBucket,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val application = context.applicationContext as CrewTallyApplication
    val database = application.database
    val reportStrings = buildReportStrings()
    val viewModel: ClerkMultiProjectStatementShareViewModel = viewModel(
        key = "clerk-statement-$clerkId-$bucket",
        factory = ClerkMultiProjectStatementShareViewModel.factory(
            clerkId = clerkId,
            bucket = bucket,
            clerkDao = database.clerkDao(),
            projectDao = database.projectDao(),
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
    // v1.2 a11y fix: fold the clerk's name into the title (matches ClerkBalanceScreen /
    // ClerkProfileScreen putting clerk identity in the app bar) instead of a bucket-only title
    // that reads the same for every clerk. Falls back to the plain title while loading, since
    // state.clerkName is empty until then.
    val title = when {
        !state.isLoaded && bucket == ClerkProjectBucket.ACTIVE -> stringResource(R.string.report_clerk_active_share_title)
        !state.isLoaded -> stringResource(R.string.report_clerk_history_share_title)
        bucket == ClerkProjectBucket.ACTIVE ->
            stringResource(R.string.report_clerk_active_share_title_with_name, state.clerkName)
        else -> stringResource(R.string.report_clerk_history_share_title_with_name, state.clerkName)
    }

    LaunchedEffect(Unit) {
        viewModel.shareRequests.collect { request -> context.shareFile(request.file, request.mimeType) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, modifier = Modifier.semantics { heading() }) },
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
                CrewTallyReportRangeSelector(
                    preset = state.rangePreset,
                    onPresetChange = { viewModel.onEvent(ClerkMultiProjectStatementShareEvent.RangePresetChanged(it)) },
                    customStart = state.customStart,
                    customEnd = state.customEnd,
                    onCustomStartChange = { viewModel.onEvent(ClerkMultiProjectStatementShareEvent.CustomStartChanged(it)) },
                    onCustomEndChange = { viewModel.onEvent(ClerkMultiProjectStatementShareEvent.CustomEndChanged(it)) },
                )

                // v1.2 a11y fix: one live-region container around whichever of the three states
                // is showing, so a range change that flips content <-> empty message gets
                // announced (mirrors CrewTallyInlineError's own Polite live region).
                val emptyMessageText = emptyMessage(state, bucket)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                ) {
                    if (state.isRangeInvalid) {
                        CrewTallyInlineError(text = stringResource(R.string.report_range_invalid))
                    } else if (!state.hasContent) {
                        Text(
                            text = emptyMessageText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        StatementPreview(state = state)
                    }
                }

                val shareAsTextLabel = stringResource(R.string.report_share_as_text)
                val shareAsPdfLabel = stringResource(R.string.report_share_as_pdf)
                CrewTallyButton(
                    text = shareAsTextLabel,
                    onClick = { viewModel.onEvent(ClerkMultiProjectStatementShareEvent.ShareAsText) },
                    enabled = !state.isGenerating && !state.isRangeInvalid && state.hasContent,
                    contentDescription = shareButtonDisabledDescription(
                        label = shareAsTextLabel,
                        isGenerating = state.isGenerating,
                        isRangeInvalid = state.isRangeInvalid,
                        hasContent = state.hasContent,
                        noContentReasonText = emptyMessageText,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                CrewTallyButton(
                    text = shareAsPdfLabel,
                    onClick = { viewModel.onEvent(ClerkMultiProjectStatementShareEvent.ShareAsPdf) },
                    enabled = !state.isGenerating && !state.isRangeInvalid && state.hasContent,
                    contentDescription = shareButtonDisabledDescription(
                        label = shareAsPdfLabel,
                        isGenerating = state.isGenerating,
                        isRangeInvalid = state.isRangeInvalid,
                        hasContent = state.hasContent,
                        noContentReasonText = emptyMessageText,
                    ),
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
            onDismiss = { viewModel.onEvent(ClerkMultiProjectStatementShareEvent.DismissGenerationFailure) },
        )
    }
}

/**
 * "No projects in this bucket at all" and "projects exist, but nothing inside the selected
 * range" are different problems with different fixes (add the clerk to a project / widen the
 * range), so they get different wording.
 */
@Composable
private fun emptyMessage(state: ClerkMultiProjectStatementShareUiState, bucket: ClerkProjectBucket): String = when {
    state.bucketProjectCount > 0 -> stringResource(R.string.report_clerk_range_empty)
    bucket == ClerkProjectBucket.ACTIVE -> stringResource(R.string.report_clerk_active_empty)
    else -> stringResource(R.string.report_clerk_history_empty)
}

/** Project count plus one earned/paid/owed card per currency (cross-currency never summed — LOCKED). */
@Composable
private fun StatementPreview(state: ClerkMultiProjectStatementShareUiState, modifier: Modifier = Modifier) {
    val statement = state.statement ?: return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.report_projects_count_label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = pluralStringResource(R.plurals.report_project_count, statement.sections.size, statement.sections.size),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        statement.grandTotalsByCurrency.forEach { total ->
            val symbol = CurrencyCodes.symbolFor(total.currency)
            val earnedText = Money.formatSignedWithSymbol(total.earned + total.extras, symbol)
            val paidText = Money.formatWithSymbol(total.paid, symbol)
            val owedText = owedDisplayText(total.owed, symbol)
            CrewTallyCurrencyTotalCard(
                currencyCode = total.currency,
                earnedLabel = stringResource(R.string.report_earned_label),
                paidLabel = stringResource(R.string.report_paid_label),
                owedLabel = stringResource(R.string.report_owed_label),
                earnedText = earnedText,
                paidText = paidText,
                owedText = owedText,
                contentDescription = stringResource(
                    R.string.report_currency_total_description,
                    CurrencyCodes.displayNameFor(total.currency),
                    earnedText,
                    paidText,
                    owedText,
                ),
            )
        }
    }
}
