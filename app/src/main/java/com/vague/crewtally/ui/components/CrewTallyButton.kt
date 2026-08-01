package com.vague.crewtally.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * The app's primary button and the pattern-setter for every action later phases add.
 *
 * Sized for the ~55-year-old primary user: it is at least [CrewTallyDimens.primaryTarget]
 * (56dp) tall and its label runs at `labelLarge` (18sp, semibold). Colors, shape, and
 * spacing all come from the theme layer — this component holds no literal dp/sp/color.
 *
 * @param text visible button label; also used as the accessibility name.
 * @param contentDescription overrides the accessibility name when the visible label is not
 *   descriptive enough on its own (e.g. an icon-led action). Defaults to [text].
 */
@Composable
fun CrewTallyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    contentDescription: String = text,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CrewTallyShape.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = PaddingValues(
            horizontal = CrewTallyTheme.dimens.spaceXl,
            vertical = CrewTallyTheme.dimens.spaceSm,
        ),
        modifier = modifier
            .heightIn(min = CrewTallyTheme.dimens.primaryTarget)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null, // labelled by the button's own contentDescription
                modifier = Modifier.size(CrewTallyTheme.dimens.iconMd),
            )
            Spacer(Modifier.width(CrewTallyTheme.dimens.spaceSm))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
