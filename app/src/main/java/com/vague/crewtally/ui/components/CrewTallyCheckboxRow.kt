package com.vague.crewtally.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * A big whole-row checkbox — e.g. the roster multi-select list in the create-project flow.
 * The entire row is the toggle target ([Modifier.toggleable] with `role = Checkbox`, at
 * least [CrewTallyDimens.primaryTarget] tall); the inner [Checkbox] has its own click
 * handler disabled so TalkBack announces the row once, not twice.
 */
@Composable
fun CrewTallyCheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = CrewTallyShape.row,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Checkbox,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = CrewTallyTheme.dimens.primaryTarget)
                .padding(
                    horizontal = CrewTallyTheme.dimens.spaceLg,
                    vertical = CrewTallyTheme.dimens.spaceSm,
                ),
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Spacer(Modifier.width(CrewTallyTheme.dimens.spaceLg))
            Column {
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
        }
    }
}
