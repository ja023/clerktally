package com.vague.crewtally.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * The app's standard text input and the pattern-setter for every form field later phases
 * add. Renders a static label ABOVE the field (Phase 1 LOCKED forms decision — "label
 * above field, big inputs") rather than Material's floating-label-inside-the-box motif,
 * because a static label stays legible at every font scale for the ~55-year-old primary
 * user instead of shrinking into the border.
 *
 * Field text runs at `bodyLarge` (18sp) — one of the "big inputs" this app promises.
 *
 * @param onFocusChanged used by [ContactSearchField] to know when the name field gains
 *   focus (to trigger the permission ask / live search); most call sites leave it null.
 */
@Composable
fun CrewTallyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
    onFocusChanged: ((FocusState) -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceXs),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .let { base -> if (onFocusChanged != null) base.onFocusChanged(onFocusChanged) else base }
                .let { base -> if (label != null) base.semantics { contentDescription = label } else base },
            placeholder = placeholder?.let {
                { Text(text = it, style = MaterialTheme.typography.bodyLarge) }
            },
            leadingIcon = leadingIcon?.let {
                { Icon(imageVector = it, contentDescription = null) }
            },
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            isError = isError,
            supportingText = supportingText?.let {
                { Text(text = it, style = MaterialTheme.typography.bodySmall) }
            },
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = CrewTallyShape.field,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}
