package com.vague.crewtally.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * The app's one confirmation dialog, used for every destructive or state-changing action
 * that needs a second tap: archive, unarchive, delete. Title and body run at `titleLarge`
 * / `bodyLarge` (both well above the 16sp floor) so the confirmation reads as plainly as
 * the rest of the app.
 *
 * @param isDestructive tints the confirm button in the error color (delete); archive and
 *   unarchive leave it false since they are reversible, not destructive.
 */
@Composable
fun CrewTallyConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) },
        text = { Text(text = body, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (isDestructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
                modifier = Modifier.heightIn(min = CrewTallyTheme.dimens.minTarget),
            ) {
                Text(text = confirmLabel, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = CrewTallyTheme.dimens.minTarget),
            ) {
                Text(text = dismissLabel, style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
