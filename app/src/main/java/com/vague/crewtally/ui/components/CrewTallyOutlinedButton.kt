package com.vague.crewtally.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * The app's secondary action button: outlined, not filled, so it reads as "available but not
 * the primary path" without dropping to a bare [androidx.compose.material3.TextButton]'s low
 * visual weight — the shape a distinct-but-not-primary action (e.g. "Share company report" on a
 * form whose primary action is Save) needs. Sized at [CrewTallyDimens.minTarget] (48dp, not the
 * 56dp primary target) matching every other secondary action in the app.
 *
 * @param text visible button label; also used as the accessibility name.
 * @param contentDescription overrides the accessibility name when the visible label alone isn't descriptive enough.
 */
@Composable
fun CrewTallyOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = text,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CrewTallyShape.button,
        border = BorderStroke(CrewTallyTheme.dimens.borderThin, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(
            horizontal = CrewTallyTheme.dimens.spaceXl,
            vertical = CrewTallyTheme.dimens.spaceSm,
        ),
        modifier = modifier
            .heightIn(min = CrewTallyTheme.dimens.minTarget)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
