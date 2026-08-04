package com.vague.crewtally.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * A blocking, form-level error banner: a warning icon plus plain-wording body text, announced
 * once via a Polite live region. Unlike attendance's `SoftNotice` (a soft, non-blocking notice
 * that reuses this same error styling for informational copy — see CLAUDE.md's review-debt
 * note on that tone mismatch), this component is for genuine validation errors — e.g. the v1.1
 * report range's "From is after To" state — so the `errorContainer` color pairing is the
 * correct semantic fit here. The icon carries the signal redundantly to color alone (LOCKED
 * a11y rule: state must never be color-only).
 */
@Composable
fun CrewTallyInlineError(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = CrewTallyShape.card,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(CrewTallyTheme.dimens.spaceLg)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Icon(imageVector = Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(CrewTallyTheme.dimens.spaceMd))
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
