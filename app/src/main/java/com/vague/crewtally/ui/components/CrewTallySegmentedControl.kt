package com.vague.crewtally.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * A big two-way (or more) segmented control — e.g. the Projects tab's Active/Completed
 * switch. Each half is a full [CrewTallyDimens.primaryTarget]-tall tap target with an
 * unmistakable filled selected state, matching the "shallow, obvious navigation" design
 * constraint (no subtle tabs). Built on [Modifier.selectable] so TalkBack announces each
 * option's selected state on its own.
 */
@Composable
fun <T> CrewTallySegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // A fixed .height() clips at large font scales (200% dynamic type) since the
            // option labels no longer fit the exact 56dp band — a minimum lets the control
            // grow with the text instead of cutting it off.
            .heightIn(min = CrewTallyTheme.dimens.primaryTarget)
            .clip(CrewTallyShape.pill)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(CrewTallyTheme.dimens.spaceXxs),
    ) {
        // selectableGroup() so TalkBack announces "N of M" for each option instead of
        // treating them as unrelated tabs.
        Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = CrewTallyTheme.dimens.primaryTarget)
                        .clip(RoundedCornerShape(CrewTallyTheme.radius.pill))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .selectable(
                            selected = isSelected,
                            onClick = { onSelect(value) },
                            role = Role.Tab,
                        ),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
