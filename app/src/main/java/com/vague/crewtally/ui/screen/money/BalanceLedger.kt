package com.vague.crewtally.ui.screen.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.vague.crewtally.R
import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ExtraPayLineWithDate
import com.vague.crewtally.data.local.PaymentEntity
import com.vague.crewtally.ui.components.CrewTallyListRow
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.theme.CrewTallyType
import com.vague.crewtally.ui.util.accessibleMoneyDescription
import com.vague.crewtally.util.Money
import java.time.format.DateTimeFormatter

private val LedgerDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

/**
 * The three ledger sections a clerk's balance breaks down into. Extracted as standalone
 * composables so the Phase 5 clerk-statement report can render the exact same breakdown the
 * balance screen shows, from the same entity shapes — reuse [DaysWorkedSection],
 * [ExtrasLedgerSection], and [PaymentsLedgerSection] rather than re-deriving report rows.
 */

/** Present-day earnings: one row per present day (date, "Present", rate snapshot), with a subtotal. */
@Composable
fun DaysWorkedSection(
    presentDays: List<AttendanceEntryEntity>,
    earnedTotal: Long,
    currencySymbol: String,
    modifier: Modifier = Modifier,
) {
    LedgerSection(
        heading = stringResource(R.string.balance_days_heading),
        subtotal = Money.formatWithSymbol(earnedTotal, currencySymbol),
        subtotalDescription = Money.formatWithSymbol(earnedTotal, currencySymbol),
        isEmpty = presentDays.isEmpty(),
        emptyText = stringResource(R.string.balance_days_empty),
        modifier = modifier,
    ) {
        presentDays.forEach { day ->
            val dateText = day.date.format(LedgerDateFormat)
            val presentText = stringResource(R.string.balance_day_present)
            val rateText = Money.formatWithSymbol(day.rateSnapshot, currencySymbol)
            CrewTallyListRow(
                title = dateText,
                subtitle = presentText,
                contentDescription = stringResource(R.string.balance_day_row_description, dateText, rateText),
                trailing = { MoneyTrailing(rateText) },
            )
        }
    }
}

/** Signed extra-pay lines: one row per line (label, date, signed amount), with a subtotal. */
@Composable
fun ExtrasLedgerSection(
    extraLines: List<ExtraPayLineWithDate>,
    extrasTotal: Long,
    currencySymbol: String,
    modifier: Modifier = Modifier,
) {
    LedgerSection(
        heading = stringResource(R.string.balance_extras_heading),
        subtotal = Money.formatSignedWithSymbol(extrasTotal, currencySymbol),
        subtotalDescription = accessibleMoneyDescription(extrasTotal, currencySymbol),
        isEmpty = extraLines.isEmpty(),
        emptyText = stringResource(R.string.balance_extras_empty),
        modifier = modifier,
    ) {
        extraLines.forEach { withDate ->
            val line = withDate.line
            val dateText = withDate.date.format(LedgerDateFormat)
            val amountText = Money.formatSignedWithSymbol(line.amount, currencySymbol)
            val amountDescription = accessibleMoneyDescription(line.amount, currencySymbol)
            CrewTallyListRow(
                title = line.label,
                subtitle = dateText,
                contentDescription = stringResource(
                    R.string.balance_extra_row_description,
                    dateText,
                    line.label,
                    amountDescription,
                ),
                trailing = { MoneyTrailing(amountText) },
            )
        }
    }
}

/** Payments: one tappable row per payment (date, note, amount), opening its edit form. */
@Composable
fun PaymentsLedgerSection(
    payments: List<PaymentEntity>,
    paidTotal: Long,
    currencySymbol: String,
    onPaymentClick: (PaymentEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    LedgerSection(
        heading = stringResource(R.string.balance_payments_heading),
        subtotal = Money.formatWithSymbol(paidTotal, currencySymbol),
        subtotalDescription = Money.formatWithSymbol(paidTotal, currencySymbol),
        isEmpty = payments.isEmpty(),
        emptyText = stringResource(R.string.balance_payments_empty),
        modifier = modifier,
    ) {
        payments.forEach { payment ->
            val dateText = payment.date.format(LedgerDateFormat)
            val amountText = Money.formatWithSymbol(payment.amount, currencySymbol)
            val descriptionSuffix = if (payment.note.isBlank()) "" else " ${payment.note}"
            CrewTallyListRow(
                title = dateText,
                subtitle = payment.note.ifBlank { null },
                onClick = { onPaymentClick(payment) },
                contentDescription = stringResource(
                    R.string.balance_payment_row_description,
                    dateText,
                    amountText,
                ) + descriptionSuffix,
                trailing = { MoneyTrailing(amountText) },
            )
        }
    }
}

@Composable
private fun MoneyTrailing(text: String) {
    Text(
        text = text,
        style = CrewTallyType.moneySmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * Shared frame for a ledger section: a heading with a subtotal on the right, then either an
 * empty line or the section's rows.
 */
@Composable
private fun LedgerSection(
    heading: String,
    subtotal: String,
    subtotalDescription: String,
    isEmpty: Boolean,
    emptyText: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val headingDescription = if (isEmpty) heading else "$heading. $subtotalDescription"
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CrewTallyTheme.dimens.spaceSm)
                .semantics(mergeDescendants = true) {
                    heading()
                    contentDescription = headingDescription
                },
        ) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (!isEmpty) {
                Text(
                    text = subtotal,
                    style = CrewTallyType.moneySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isEmpty) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceSm)) {
                content()
            }
        }
    }
}
