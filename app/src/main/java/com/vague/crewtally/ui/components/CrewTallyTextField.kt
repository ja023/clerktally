package com.vague.crewtally.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * The app's standard text input and the pattern-setter for every form field later phases add:
 * a visible label ABOVE the field (not a floating Material label) per the senior-friendly
 * "label above field" form convention, and a field tall enough to clear
 * [CrewTallyDimens.primaryTarget] so it's an easy target even before it's focused.
 *
 * @param leadingText optional fixed prefix shown inside the field (e.g. a currency symbol).
 * @param supportingText optional helper/error line below the field.
 */
@Composable
fun CrewTallyTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    leadingText: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CrewTallyTheme.dimens.spaceXs))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            isError = isError,
            keyboardOptions = keyboardOptions,
            shape = CrewTallyShape.field,
            textStyle = MaterialTheme.typography.bodyLarge,
            leadingIcon = leadingText?.let {
                { Text(text = it, style = MaterialTheme.typography.bodyLarge) }
            },
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
                .fillMaxWidth()
                .heightIn(min = CrewTallyTheme.dimens.primaryTarget),
        )
    }
}
