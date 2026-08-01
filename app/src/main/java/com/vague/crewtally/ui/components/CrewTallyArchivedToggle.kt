package com.vague.crewtally.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * The "Show archived" row that sits at the bottom of both the Clerks and Companies lists
 * (Phase 1 LOCKED archive/delete UX). One shared component so the toggle looks and behaves
 * identically on both screens. The whole row is the tap target (>= 48dp), not just the
 * switch thumb, and is exposed to TalkBack as a single switch-role node.
 */
@Composable
fun CrewTallyArchivedToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CrewTallyTheme.dimens.minTarget)
            .padding(vertical = CrewTallyTheme.dimens.spaceSm)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // The row itself owns the toggle semantics/click; the Switch here is purely
        // visual (onCheckedChange = null) so a tap doesn't fire twice.
        Switch(checked = checked, onCheckedChange = null)
    }
}
