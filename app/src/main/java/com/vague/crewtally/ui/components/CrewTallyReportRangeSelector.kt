package com.vague.crewtally.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vague.crewtally.R
import com.vague.crewtally.report.ReportRangePreset
import com.vague.crewtally.ui.theme.CrewTallyTheme
import java.time.LocalDate

/**
 * The v1.1 date-range selector shared by the project statement and company statement share
 * screens: a big four-way [CrewTallySegmentedControl] (All time / This month / Last month /
 * Custom — LOCKED order and default), revealing two [CrewTallyDateField]s only when Custom is
 * selected (LOCKED: "Range selection lives ON the share screens... Custom reveals two date
 * pickers... Keep navigation shallow — no separate range screen").
 *
 * [customStart]/[customEnd] are nullable because they only carry a meaningful value once the
 * caller's ViewModel has selected CUSTOM (which initializes both to today the moment CUSTOM is
 * picked, so displayed and applied dates never disagree — see `onRangePresetChanged` on the
 * share ViewModels). The `today` fallback here is defensive only: it is never actually reached
 * on the CUSTOM path once that ViewModel-side initialization has run, so callers no longer need
 * their own `?: today` fallback.
 */
@Composable
fun CrewTallyReportRangeSelector(
    preset: ReportRangePreset,
    onPresetChange: (ReportRangePreset) -> Unit,
    customStart: LocalDate?,
    customEnd: LocalDate?,
    onCustomStartChange: (LocalDate) -> Unit,
    onCustomEndChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd),
    ) {
        CrewTallySegmentedControl(
            options = listOf(
                ReportRangePreset.ALL_TIME to stringResource(R.string.report_range_all_time),
                ReportRangePreset.THIS_MONTH to stringResource(R.string.report_range_this_month),
                ReportRangePreset.LAST_MONTH to stringResource(R.string.report_range_last_month),
                ReportRangePreset.CUSTOM to stringResource(R.string.report_range_custom),
            ),
            selected = preset,
            onSelect = onPresetChange,
        )

        if (preset == ReportRangePreset.CUSTOM) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CrewTallyDateField(
                    label = stringResource(R.string.report_range_from),
                    value = customStart ?: LocalDate.now(),
                    onValueChange = onCustomStartChange,
                    modifier = Modifier.weight(1f),
                )
                CrewTallyDateField(
                    label = stringResource(R.string.report_range_to),
                    value = customEnd ?: LocalDate.now(),
                    onValueChange = onCustomEndChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
