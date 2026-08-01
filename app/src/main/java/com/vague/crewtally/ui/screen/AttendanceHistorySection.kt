package com.vague.crewtally.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.vague.crewtally.R
import com.vague.crewtally.data.local.AttendanceDaySummary
import com.vague.crewtally.ui.components.CrewTallyListRow
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.theme.CrewTallyType
import com.vague.crewtally.ui.util.accessibleMoneyDescription
import com.vague.crewtally.util.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

/**
 * The project-detail attendance history: one compact row per recorded day (newest first),
 * showing the present count and the day's extras total, each opening that day's attendance
 * screen. Lean by design — projects run weeks, so every day is shown without paging.
 */
@Composable
fun AttendanceHistorySection(
    days: List<AttendanceDaySummary>,
    currencySymbol: String,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.attendance_history_heading),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(bottom = CrewTallyTheme.dimens.spaceSm)
                .semantics { heading() },
        )

        if (days.isEmpty()) {
            Text(
                text = stringResource(R.string.attendance_history_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd)) {
                days.forEach { summary ->
                    val dateText = summary.date.format(DateFormat)
                    val presentText = stringResource(R.string.attendance_history_present_count, summary.presentCount)
                    val extrasText = Money.formatSignedWithSymbol(summary.extrasTotal, currencySymbol)
                    val extrasDescription = accessibleMoneyDescription(summary.extrasTotal, currencySymbol)
                    CrewTallyListRow(
                        title = dateText,
                        subtitle = presentText,
                        onClick = { onDayClick(summary.date) },
                        contentDescription = stringResource(
                            R.string.attendance_history_row_description,
                            dateText,
                            summary.presentCount,
                            extrasDescription,
                        ),
                        trailing = if (summary.extrasTotal != 0L) {
                            {
                                Text(
                                    text = extrasText,
                                    style = CrewTallyType.moneySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}
