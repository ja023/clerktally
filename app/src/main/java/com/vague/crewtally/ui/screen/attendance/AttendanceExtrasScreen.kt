package com.vague.crewtally.ui.screen.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vague.crewtally.CrewTallyApplication
import com.vague.crewtally.R
import com.vague.crewtally.data.local.ExtraPayLineEntity
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.components.CrewTallyCheckboxRow
import com.vague.crewtally.ui.components.CrewTallySegmentedControl
import com.vague.crewtally.ui.components.CrewTallyTextField
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.theme.CrewTallyType
import com.vague.crewtally.ui.util.accessibleMoneyDescription
import com.vague.crewtally.ui.viewmodel.AttendanceExtrasEvent
import com.vague.crewtally.ui.viewmodel.AttendanceExtrasViewModel
import com.vague.crewtally.ui.viewmodel.ExtraPreset
import com.vague.crewtally.util.CurrencyCodes
import com.vague.crewtally.util.Money
import java.time.LocalDate

/**
 * Full-screen extras editor for one clerk on one day (LOCKED "forms are full-screen pages").
 * Preset labels (Lunch / Transport / Bonus) plus free-text Custom, an amount via the money
 * field, and an explicit Deduction toggle so the user never types a minus sign. Extras are
 * allowed on any status — adding the first one lazily creates a present=false carrier row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceExtrasScreen(
    projectId: String,
    clerkId: String,
    date: LocalDate,
    clerkName: String,
    rateMinorUnits: Long,
    currency: String,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as CrewTallyApplication
    val database = application.database
    val viewModel: AttendanceExtrasViewModel = viewModel(
        factory = AttendanceExtrasViewModel.factory(
            projectId = projectId,
            clerkId = clerkId,
            date = date,
            rateSnapshot = rateMinorUnits,
            attendanceEntryDao = database.attendanceEntryDao(),
            extraPayLineDao = database.extraPayLineDao(),
            attendanceWriter = application.attendanceWriter,
        ),
    )

    val state by viewModel.state.collectAsStateWithLifecycle()
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()
    val symbol = CurrencyCodes.symbolFor(currency)

    val presetOptions = listOf(
        ExtraPreset.LUNCH to stringResource(R.string.extras_preset_lunch),
        ExtraPreset.TRANSPORT to stringResource(R.string.extras_preset_transport),
        ExtraPreset.BONUS to stringResource(R.string.extras_preset_bonus),
        ExtraPreset.CUSTOM to stringResource(R.string.extras_preset_custom),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.extras_title, clerkName),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(CrewTallyTheme.dimens.screenEdge),
            verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceLg),
        ) {
            CrewTallySegmentedControl(
                options = presetOptions,
                selected = state.preset,
                onSelect = { viewModel.onEvent(AttendanceExtrasEvent.PresetChanged(it)) },
            )

            if (state.preset == ExtraPreset.CUSTOM) {
                CrewTallyTextField(
                    label = stringResource(R.string.extras_custom_label),
                    value = state.customLabel,
                    onValueChange = { viewModel.onEvent(AttendanceExtrasEvent.CustomLabelChanged(it)) },
                    isError = state.customLabelError,
                    supportingText = if (state.customLabelError) stringResource(R.string.extras_custom_label_error) else null,
                )
            }

            CrewTallyTextField(
                label = stringResource(R.string.extras_amount_label),
                value = state.amountInput,
                onValueChange = { viewModel.onEvent(AttendanceExtrasEvent.AmountChanged(it)) },
                leadingText = symbol,
                isError = state.amountError,
                supportingText = if (state.amountError) stringResource(R.string.extras_amount_error) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            CrewTallyCheckboxRow(
                title = stringResource(R.string.extras_deduction_toggle),
                checked = state.isDeduction,
                onCheckedChange = { viewModel.onEvent(AttendanceExtrasEvent.DeductionChanged(it)) },
            )

            CrewTallyButton(
                text = stringResource(R.string.extras_add_line),
                onClick = { viewModel.onEvent(AttendanceExtrasEvent.AddLine) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(CrewTallyTheme.dimens.spaceXs))

            Text(
                text = stringResource(R.string.extras_lines_heading),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )

            if (lines.isEmpty()) {
                Text(
                    text = stringResource(R.string.extras_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceSm)) {
                    lines.forEach { line ->
                        ExtraLineRow(
                            line = line,
                            symbol = symbol,
                            onDelete = { viewModel.onEvent(AttendanceExtrasEvent.DeleteLine(line)) },
                        )
                    }
                }
                TotalRow(total = total, symbol = symbol)
            }
        }
    }
}

@Composable
private fun ExtraLineRow(
    line: ExtraPayLineEntity,
    symbol: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val amountText = Money.formatSignedWithSymbol(line.amount, symbol)
    val amountDescription = accessibleMoneyDescription(line.amount, symbol)
    val lineDescription = stringResource(R.string.extras_line_description, line.label, amountDescription)
    val deleteLabel = stringResource(R.string.extras_delete_line_named, line.label, amountDescription)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = CrewTallyShape.row,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = CrewTallyTheme.dimens.spaceLg,
                top = CrewTallyTheme.dimens.spaceSm,
                bottom = CrewTallyTheme.dimens.spaceSm,
                end = CrewTallyTheme.dimens.spaceSm,
            ),
        ) {
            // One merged node for label + amount — a single "<label>, <amount>" announcement
            // instead of TalkBack reading the label's own text AND a contentDescription override
            // AND the amount separately (three overlapping announcements for one fact).
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = lineDescription },
            ) {
                Text(
                    text = line.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = amountText,
                    style = CrewTallyType.moneySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = deleteLabel,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(CrewTallyTheme.dimens.iconMd),
                )
            }
        }
    }
}

@Composable
private fun TotalRow(total: Long, symbol: String, modifier: Modifier = Modifier) {
    val totalDescription = accessibleMoneyDescription(total, symbol)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CrewTallyTheme.dimens.spaceLg)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = stringResource(R.string.extras_total),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = Money.formatSignedWithSymbol(total, symbol),
            style = CrewTallyType.moneyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { contentDescription = totalDescription },
        )
    }
}
