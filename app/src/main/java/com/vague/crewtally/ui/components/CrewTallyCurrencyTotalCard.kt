package com.vague.crewtally.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.theme.CrewTallyType

/**
 * One currency's earned/paid/owed card — shared by the clerk profile totals and the company
 * report preview (both show the same "one card per currency, never summed across currencies"
 * shape — LOCKED). Extracted so the two screens can't drift on how this reads.
 */
@Composable
fun CrewTallyCurrencyTotalCard(
    currencyCode: String,
    earnedLabel: String,
    paidLabel: String,
    owedLabel: String,
    earnedText: String,
    paidText: String,
    owedText: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = CrewTallyShape.card,
        tonalElevation = CrewTallyTheme.dimens.elevationCard,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CrewTallyTheme.dimens.spaceLg)
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
            verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceSm),
        ) {
            Text(
                text = currencyCode,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CurrencyTotalLine(label = earnedLabel, value = earnedText)
            CurrencyTotalLine(label = paidLabel, value = paidText)
            CurrencyTotalLine(label = owedLabel, value = owedText, emphasise = true)
        }
    }
}

@Composable
private fun CurrencyTotalLine(label: String, value: String, emphasise: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (emphasise) CrewTallyType.moneySmall else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
