package com.vague.crewtally.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * The app's standard "nothing here yet" state for a list screen: a short headline, one
 * line of guidance, and a clear call-to-action button. Used by the Clerks and Companies
 * list screens (Phase 1) and any future list that can start out empty.
 *
 * The CTA reuses [CrewTallyButton] rather than inventing a second button style, so the
 * empty state's action looks identical to the persistent add button underneath it.
 */
@Composable
fun CrewTallyEmptyState(
    title: String,
    body: String,
    ctaLabel: String,
    onCtaClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(CrewTallyTheme.dimens.spaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceLg),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CrewTallyButton(text = ctaLabel, onClick = onCtaClick)
    }
}
