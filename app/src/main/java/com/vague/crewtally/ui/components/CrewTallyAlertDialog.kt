package com.vague.crewtally.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * A single-action informational alert (title + body + one acknowledgment button) — for cases
 * that need the user's attention but aren't a yes/no decision, unlike [CrewTallyConfirmDialog].
 * The restore-failure alert (Phase 5) is the first user; kept here as a shared component rather
 * than an inline `AlertDialog` so any later one-button alert looks and behaves identically.
 */
@Composable
fun CrewTallyAlertDialog(title: String, body: String, actionLabel: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) },
        text = { Text(text = body, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = CrewTallyTheme.dimens.minTarget)) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
