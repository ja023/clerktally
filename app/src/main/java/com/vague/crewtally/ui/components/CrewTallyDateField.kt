package com.vague.crewtally.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vague.crewtally.R
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A label-above-field date picker: a tappable field showing the current date in plain text,
 * opening Material's [DatePickerDialog] on tap. Dates are calendar days with no time
 * component (matches [LocalDate] storage) so the millis round-trip pins to UTC midnight —
 * there is no timezone meaning to preserve here, only a day number.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewTallyDateField(
    label: String,
    value: LocalDate,
    onValueChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val formatted = remember(value) { value.format(DateTimeFormatter.ofPattern("d MMM yyyy")) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CrewTallyTheme.dimens.spaceXs))
        Surface(
            onClick = { showPicker = true },
            shape = CrewTallyShape.field,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(CrewTallyTheme.dimens.borderThin, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CrewTallyTheme.dimens.primaryTarget)
                .semantics { contentDescription = "$label. $formatted" },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = CrewTallyTheme.dimens.spaceLg),
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarToday,
                    contentDescription = null, // field is labelled as a whole
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(CrewTallyTheme.dimens.spaceMd))
                Text(text = formatted, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    if (showPicker) {
        val initialMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onValueChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
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
