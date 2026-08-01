package com.vague.crewtally.ui.screen.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.vague.crewtally.R
import com.vague.crewtally.ui.components.AttendanceStateToggle
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.theme.CrewTallyType
import com.vague.crewtally.ui.util.accessibleMoneyDescription
import com.vague.crewtally.ui.viewmodel.AttendanceRowUi
import com.vague.crewtally.util.Money

/**
 * One clerk's card on the attendance day screen: name + daily rate up top (with a walk-in tag
 * where relevant), the big three-state [AttendanceStateToggle] beneath, and a labelled Extras
 * affordance showing the day's extras total when non-zero. All auto-saving — no per-row save.
 */
@Composable
fun AttendanceClerkRow(
    row: AttendanceRowUi,
    currencySymbol: String,
    onPresent: () -> Unit,
    onAbsent: () -> Unit,
    onExtras: () -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.clerkName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (row.isWalkIn) {
                        Text(
                            text = stringResource(R.string.attendance_walk_in_tag),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val rateDescription = stringResource(
                    R.string.attendance_clerk_rate_description,
                    Money.formatWithSymbol(row.rateMinorUnits, currencySymbol),
                )
                Text(
                    text = Money.formatWithSymbol(row.rateMinorUnits, currencySymbol),
                    style = CrewTallyType.moneySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics {
                        contentDescription = rateDescription
                    },
                )
            }

            AttendanceStateToggle(state = row.state, onPresent = onPresent, onAbsent = onAbsent)

            val extrasLabel = if (row.extrasTotalMinorUnits != 0L) {
                stringResource(
                    R.string.attendance_extras_action_with_total,
                    Money.formatSignedWithSymbol(row.extrasTotalMinorUnits, currencySymbol),
                )
            } else {
                stringResource(R.string.attendance_extras_action)
            }
            val extrasAccessibleLabel = if (row.extrasTotalMinorUnits != 0L) {
                stringResource(
                    R.string.attendance_extras_action_with_total,
                    accessibleMoneyDescription(row.extrasTotalMinorUnits, currencySymbol),
                )
            } else {
                stringResource(R.string.attendance_extras_action)
            }
            TextButton(
                onClick = onExtras,
                modifier = Modifier
                    .heightIn(min = CrewTallyTheme.dimens.minTarget)
                    .semantics { contentDescription = extrasAccessibleLabel },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                    contentDescription = null,
                    modifier = Modifier.size(CrewTallyTheme.dimens.iconSm),
                )
                Spacer(Modifier.width(CrewTallyTheme.dimens.spaceXs))
                Text(text = extrasLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
