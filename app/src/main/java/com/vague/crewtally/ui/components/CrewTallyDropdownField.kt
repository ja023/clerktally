package com.vague.crewtally.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.vague.crewtally.R
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * A label-above-field picker built on Material's exposed dropdown menu: a big, obviously
 * tappable field (not a hidden gesture — the chevron and full-field tap target make the
 * affordance visible) that opens a list of [options].
 *
 * @param editable when true the field also accepts free-typed text (e.g. an ISO currency
 *   code not in the shortlist); when false the field is read-only and a value can only come
 *   from picking an option (e.g. company, which must be a stored record).
 * @param optionLabel how to render each raw option value in the dropdown list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewTallyDropdownField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    optionLabel: (String) -> String = { it },
) {
    var expanded by remember { mutableStateOf(false) }
    val expandedStateDescription = stringResource(R.string.cd_dropdown_expanded)
    val collapsedStateDescription = stringResource(R.string.cd_dropdown_collapsed)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CrewTallyTheme.dimens.spaceXs))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { if (editable) onValueChange(it) },
                readOnly = !editable,
                isError = isError,
                shape = CrewTallyShape.field,
                textStyle = MaterialTheme.typography.bodyLarge,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                supportingText = supportingText?.let {
                    {
                        Text(
                            text = it,
                            color = if (isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
                modifier = Modifier
                    .menuAnchor(
                        type = if (editable) MenuAnchorType.PrimaryEditable else MenuAnchorType.PrimaryNotEditable,
                        enabled = true,
                    )
                    .fillMaxWidth()
                    .heightIn(min = CrewTallyTheme.dimens.primaryTarget)
                    // The bare OutlinedTextField has no accessible name of its own — the
                    // [label] Text above it is a separate node TalkBack won't associate with
                    // the field automatically, so this merges the whole thing into one
                    // announced control with its open/closed state spoken too.
                    .semantics(mergeDescendants = true) {
                        contentDescription = label
                        stateDescription = if (expanded) expandedStateDescription else collapsedStateDescription
                        role = Role.DropdownList
                    },
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option), style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
