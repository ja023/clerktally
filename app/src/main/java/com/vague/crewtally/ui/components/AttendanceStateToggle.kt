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
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.vague.crewtally.R
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.viewmodel.AttendanceState

/**
 * The three-state attendance control on each clerk row: two big [CrewTallyDimens.primaryTarget]
 * (56dp) halves, Present and Absent. State is never signalled by color alone — each half carries
 * an icon (check / cross) AND a text label, and the selected half is filled while the other is
 * outlined. Tapping the already-selected half is how the caller clears the clerk back to
 * Unmarked (both halves outlined).
 *
 * [selectableGroup] on the outer Row lets TalkBack announce the pair as a radio group. Each
 * half's [androidx.compose.ui.semantics.stateDescription] states the ROW's overall status
 * (Present / Absent / Unmarked, using [R.string.attendance_state_unmarked]) rather than relying
 * on the bare selectable "selected"/"not selected" announcement, which gave no way to
 * distinguish Unmarked (neither half selected) from any other unselected reading. The selected
 * half additionally carries a spoken hint that re-tapping clears the mark, plus a discoverable
 * "Clear mark" TalkBack custom action doing the same thing — both routes call the same [onClick]
 * the caller already wires to the unmark path when the tapped state matches the current one.
 */
@Composable
fun AttendanceStateToggle(
    state: AttendanceState,
    onPresent: () -> Unit,
    onAbsent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentStateText = when (state) {
        AttendanceState.PRESENT -> stringResource(R.string.attendance_state_present)
        AttendanceState.ABSENT -> stringResource(R.string.attendance_state_absent)
        AttendanceState.UNMARKED -> stringResource(R.string.attendance_state_unmarked)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd),
        modifier = modifier.fillMaxWidth().selectableGroup(),
    ) {
        ToggleHalf(
            selected = state == AttendanceState.PRESENT,
            icon = Icons.Filled.Check,
            label = stringResource(R.string.attendance_state_present),
            currentStateText = currentStateText,
            selectedContainer = MaterialTheme.colorScheme.secondary,
            selectedContent = MaterialTheme.colorScheme.onSecondary,
            onClick = onPresent,
            modifier = Modifier.weight(1f),
        )
        ToggleHalf(
            selected = state == AttendanceState.ABSENT,
            icon = Icons.Filled.Close,
            label = stringResource(R.string.attendance_state_absent),
            currentStateText = currentStateText,
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
    currentStateText: String,
    selectedContainer: Color,
    selectedContent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) selectedContainer else MaterialTheme.colorScheme.surfaceContainer
    val content = if (selected) selectedContent else MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (selected) null else BorderStroke(CrewTallyTheme.dimens.borderThin, MaterialTheme.colorScheme.outline)
    val rowStateDescription = stringResource(R.string.attendance_row_state_description, label, currentStateText)
    val unmarkHint = stringResource(R.string.attendance_state_toggle_unmark_hint)
    val clearActionLabel = stringResource(R.string.attendance_state_toggle_clear_action)

    Surface(
        color = container,
        contentColor = content,
        shape = CrewTallyShape.button,
        border = border,
        modifier = modifier
            .heightIn(min = CrewTallyTheme.dimens.primaryTarget)
            // selectable merges its descendants and carries the selected role/state; the
            // stateDescription below overrides its default "selected"/"not selected" reading
            // with the row's actual status (incl. Unmarked, which "not selected" alone can't
            // distinguish from any other unselected state).
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics {
                stateDescription = if (selected) "$rowStateDescription. $unmarkHint" else rowStateDescription
                if (selected) {
                    customActions = listOf(CustomAccessibilityAction(clearActionLabel) { onClick(); true })
                }
            },
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
            // fill = false + maxLines lets a long/scaled-up label wrap onto a second line at
            // 200% font instead of colliding with the icon or the neighboring half.
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
