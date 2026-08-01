package com.vague.crewtally.ui.screen.clerk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vague.crewtally.R
import com.vague.crewtally.ui.clerk.ClerkFormEvent
import com.vague.crewtally.ui.clerk.ClerkFormViewModel
import com.vague.crewtally.ui.components.ContactSearchField
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.components.CrewTallyConfirmDialog
import com.vague.crewtally.ui.components.CrewTallyTextField
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.util.crewTallyDatabase

/**
 * Full-screen add/edit form for a clerk (Phase 1 LOCKED forms decision: full-screen pages,
 * not bottom sheets, label above every field, one Save button).
 *
 * Archive/delete affordance: these three state-changing actions live HERE, on the edit
 * form, rather than behind a per-row overflow menu on the list. A row overflow (kebab
 * icon) is a hidden-by-default affordance — you have to know it's there — which cuts
 * against CLAUDE.md's "no hidden gestures, no long-press-only actions" rule for the
 * ~55-year-old primary user. Landing on the same full-screen form the user already used to
 * create the record, with the destructive actions laid out as plain labelled buttons, keeps
 * the whole edit lifecycle in one obvious place. The Company form uses the identical
 * pattern.
 */
@Composable
fun ClerkFormScreen(
    clerkId: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClerkFormViewModel = viewModel(
        factory = LocalContext.current.crewTallyDatabase().let { db ->
            ClerkFormViewModel.factory(db.clerkDao(), db.rosterEntryDao(), db.attendanceEntryDao(), db.paymentDao(), clerkId)
        },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var attemptedSave by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showUnarchiveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) onDone()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(CrewTallyTheme.dimens.screenEdge),
        verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceLg),
    ) {
        Text(
            text = stringResource(if (uiState.isEditing) R.string.clerk_form_title_edit else R.string.clerk_form_title_new),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )

        ContactSearchField(
            name = uiState.name,
            onNameChange = { viewModel.onEvent(ClerkFormEvent.NameChanged(it)) },
            phone = uiState.phone,
            onPhoneChange = { viewModel.onEvent(ClerkFormEvent.PhoneChanged(it)) },
            nameLabel = stringResource(R.string.field_name),
            phoneLabel = stringResource(R.string.field_phone),
            namePlaceholder = stringResource(R.string.field_name_search_placeholder),
            isNameError = attemptedSave && !uiState.isNameValid,
            nameSupportingText = if (attemptedSave && !uiState.isNameValid) {
                stringResource(R.string.error_name_required)
            } else {
                null
            },
        )

        CrewTallyTextField(
            value = uiState.notes,
            onValueChange = { viewModel.onEvent(ClerkFormEvent.NotesChanged(it)) },
            label = stringResource(R.string.field_notes),
            singleLine = false,
            minLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
        )

        CrewTallyButton(
            text = stringResource(R.string.action_save),
            enabled = uiState.canSave,
            onClick = {
                if (uiState.canSave) {
                    viewModel.onEvent(ClerkFormEvent.Save)
                } else {
                    attemptedSave = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (uiState.isEditing) {
            if (uiState.isArchived) {
                TextButton(
                    onClick = { showUnarchiveConfirm = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = CrewTallyTheme.dimens.minTarget),
                ) {
                    Text(text = stringResource(R.string.action_unarchive), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                TextButton(
                    onClick = { showArchiveConfirm = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = CrewTallyTheme.dimens.minTarget),
                ) {
                    Text(text = stringResource(R.string.action_archive), style = MaterialTheme.typography.labelLarge)
                }
            }
            if (uiState.canDelete) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = CrewTallyTheme.dimens.minTarget),
                ) {
                    Text(text = stringResource(R.string.action_delete), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (showArchiveConfirm) {
        CrewTallyConfirmDialog(
            title = stringResource(R.string.clerk_archive_confirm_title),
            body = stringResource(R.string.clerk_archive_confirm_body),
            confirmLabel = stringResource(R.string.action_archive),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                showArchiveConfirm = false
                viewModel.onEvent(ClerkFormEvent.Archive)
            },
            onDismiss = { showArchiveConfirm = false },
        )
    }
    if (showUnarchiveConfirm) {
        CrewTallyConfirmDialog(
            title = stringResource(R.string.clerk_unarchive_confirm_title),
            body = stringResource(R.string.clerk_unarchive_confirm_body),
            confirmLabel = stringResource(R.string.action_unarchive),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                showUnarchiveConfirm = false
                viewModel.onEvent(ClerkFormEvent.Unarchive)
            },
            onDismiss = { showUnarchiveConfirm = false },
        )
    }
    if (showDeleteConfirm) {
        CrewTallyConfirmDialog(
            title = stringResource(R.string.clerk_delete_confirm_title),
            body = stringResource(R.string.clerk_delete_confirm_body),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            isDestructive = true,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.onEvent(ClerkFormEvent.Delete)
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
