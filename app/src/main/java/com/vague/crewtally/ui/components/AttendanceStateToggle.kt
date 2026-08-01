package com.vague.crewtally.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.vague.crewtally.R
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.viewmodel.AttendanceState

/**
 * The three-state attendance control on each clerk row: two big [CrewTallyDimens.primaryTarget]
 * (56dp) halves, Present and Absent. State is never signalled by color alone — each half carries
 * an icon (check / cross) AND a text label, and the selected half is filled while the other is
 * outlined. Tapping the already-selected half is how the caller clears the clerk back to
 * Unmarked (both halves outlined); the two [selectable] halves let TalkBack announce each one's
 * selected state on its own.
 */
@Composable
fun AttendanceStateToggle(
    state: AttendanceState,
    onPresent: () -> Unit,
    onAbsent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd),
        modifier = modifier.fillMaxWidth(),
    ) {
        ToggleHalf(
            selected = state == AttendanceState.PRESENT,
            icon = Icons.Filled.Check,
            label = stringResource(R.string.attendance_state_present),
            selectedContainer = MaterialTheme.colorScheme.secondary,
            selectedContent = MaterialTheme.colorScheme.onSecondary,
            onClick = onPresent,
            modifier = Modifier.weight(1f),
        )
        ToggleHalf(
            selected = state == AttendanceState.ABSENT,
            icon = Icons.Filled.Close,
            label = stringResource(R.string.attendance_state_absent),
            selectedContainer = MaterialTheme.colorScheme.error,
            selectedContent = MaterialTheme.colorScheme.onError,
            onClick = onAbsent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ToggleHalf(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    selectedContainer: Color,
    selectedContent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) selectedContainer else MaterialTheme.colorScheme.surfaceContainer
    val content = if (selected) selectedContent else MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (selected) null else BorderStroke(CrewTallyTheme.dimens.borderThin, MaterialTheme.colorScheme.outline)

    Surface(
        color = container,
        contentColor = content,
        shape = CrewTallyShape.button,
        border = border,
        modifier = modifier
            .heightIn(min = CrewTallyTheme.dimens.primaryTarget)
            // selectable merges its descendants and carries the selected state, so TalkBack
            // announces "<label>, selected" — the inner icon has a null description, leaving
            // only the label text to name the option.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CrewTallyTheme.dimens.spaceMd, vertical = CrewTallyTheme.dimens.spaceSm),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(CrewTallyTheme.dimens.iconMd))
            Spacer(Modifier.width(CrewTallyTheme.dimens.spaceSm))
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
