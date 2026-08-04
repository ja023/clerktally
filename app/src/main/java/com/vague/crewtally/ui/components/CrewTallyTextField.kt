package com.vague.crewtally.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
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
 * @param leadingText optional fixed prefix inside the field (e.g. a currency symbol on
 *   rate inputs) — text, not an icon, so it scales with the user's font size.
 * @param focusRequester optional handle for driving focus into this field — used both for
 *   IME "Next" chaining between form fields and for moving focus onto the first field of a
 *   freshly shown wizard step (see [com.vague.crewtally.ui.util.rememberEntryFocusRequester]).
 * @param keyboardActions the IME action handlers (e.g. what "Next"/"Done" does); pair with an
 *   `imeAction` set on [keyboardOptions].
 * @param accessibilityLabel overrides the field's contentDescription without changing the
 *   visible [label] text. Used by an auto-focused wizard-step field to fold the step title into
 *   what TalkBack announces (see
 *   [com.vague.crewtally.ui.util.entryFocusFieldLabel]) while keeping the on-screen label
 *   short for sighted users. Defaults to [label].
 */
@Composable
fun CrewTallyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    leadingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((FocusState) -> Unit)? = null,
    accessibilityLabel: String? = null,
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
                .let { base -> if (focusRequester != null) base.focusRequester(focusRequester) else base }
                .let { base -> if (onFocusChanged != null) base.onFocusChanged(onFocusChanged) else base }
                .let { base ->
                    val description = accessibilityLabel ?: label
                    if (description != null) base.semantics { contentDescription = description } else base
                },
            placeholder = placeholder?.let {
                { Text(text = it, style = MaterialTheme.typography.bodyLarge) }
            },
            leadingIcon = leadingIcon?.let {
                { Icon(imageVector = it, contentDescription = null) }
            },
            prefix = leadingText?.let {
                { Text(text = it, style = MaterialTheme.typography.bodyLarge) }
            },
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
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
