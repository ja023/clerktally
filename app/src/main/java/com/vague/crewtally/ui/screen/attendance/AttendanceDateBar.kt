package com.vague.crewtally.ui.screen.attendance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.vague.crewtally.R
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

/**
 * The attendance day header: a previous/next day pair of arrows around a tappable date that
 * opens the same Material date picker [com.vague.crewtally.ui.components.CrewTallyDateField]
 * uses (backfill any date). The date text is the day screen's live heading — it is a
 * [LiveRegionMode.Polite] region so TalkBack announces the new date when the user steps days
 * or picks one, since nothing else on screen signals the change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceDateBar(
    date: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val formatted = remember(date) { date.format(DateFormat) }
    val heading = stringResource(R.string.attendance_date_heading, formatted)
    val changeDateHint = stringResource(R.string.attendance_pick_date)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceSm),
        modifier = modifier.fillMaxWidth(),
    ) {
        IconButton(
            onClick = onPrev,
            modifier = Modifier.size(CrewTallyTheme.dimens.primaryTarget),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                contentDescription = stringResource(R.string.attendance_prev_day),
                modifier = Modifier.size(CrewTallyTheme.dimens.iconMd),
            )
        }

        Surface(
            onClick = { showPicker = true },
            shape = CrewTallyShape.field,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(CrewTallyTheme.dimens.borderThin, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = CrewTallyTheme.dimens.primaryTarget)
                // Live heading: announces the selected date whenever it changes, and is
                // labelled as the tappable "change date" control for TalkBack.
                .semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "$heading. $changeDateHint"
                },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(CrewTallyTheme.dimens.spaceSm),
            ) {
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }

        IconButton(
            onClick = onNext,
            modifier = Modifier.size(CrewTallyTheme.dimens.primaryTarget),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = stringResource(R.string.attendance_next_day),
                modifier = Modifier.size(CrewTallyTheme.dimens.iconMd),
            )
        }
    }

    if (showPicker) {
        val initialMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
