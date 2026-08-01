package com.vague.crewtally.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * The app's standard large list row and the pattern-setter for every list surface later
 * phases build (clerks, projects, roster, payments).
 *
 * Large and legible for the ~55-year-old primary user: at least
 * [CrewTallyDimens.listRowMinHeight] (64dp) tall, title at `titleMedium` (18sp), and — when
 * the row is tappable — a touch target that already clears the 48dp floor. A leading icon,
 * a supporting line, and a trailing slot (for money, a chevron, a toggle) are all optional
 * so one component covers most rows in the app.
 *
 * @param title primary text (>= 18sp).
 * @param subtitle optional supporting line.
 * @param onClick when non-null the whole row is clickable and exposed as one a11y node.
 * @param contentDescription accessibility name for the row; defaults to [title]. Supply a
 *   richer description (e.g. including the balance) where the visible title alone is not
 *   enough for a screen-reader user.
 * @param trailing optional trailing content, typically a money amount or a chevron.
 */
@Composable
fun CrewTallyListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentDescription: String = title,
    trailing: (@Composable () -> Unit)? = null,
) {
    val rowSemantics = Modifier.semantics(mergeDescendants = true) {
        this.contentDescription = contentDescription
    }

    Surface(
        onClick = onClick ?: {},
        enabled = enabled && onClick != null,
        shape = CrewTallyShape.row,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = CrewTallyTheme.dimens.elevationCard,
        modifier = modifier.fillMaxWidth().then(rowSemantics),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = CrewTallyTheme.dimens.listRowMinHeight)
                .padding(
                    horizontal = CrewTallyTheme.dimens.spaceLg,
                    vertical = CrewTallyTheme.dimens.rowVertical,
                ),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null, // row is labelled as a whole
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(CrewTallyTheme.dimens.iconLg),
                )
                Spacer(Modifier.width(CrewTallyTheme.dimens.spaceLg))
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceXxs),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (trailing != null) {
                Spacer(Modifier.width(CrewTallyTheme.dimens.spaceLg))
                trailing()
            }
        }
    }
}
