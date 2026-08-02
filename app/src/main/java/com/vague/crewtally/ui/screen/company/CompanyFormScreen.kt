package com.vague.crewtally.ui.screen.company

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
import com.vague.crewtally.ui.company.CompanyFormEvent
import com.vague.crewtally.ui.company.CompanyFormViewModel
import com.vague.crewtally.ui.components.ContactSearchField
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.components.CrewTallyConfirmDialog
import com.vague.crewtally.ui.components.CrewTallyTextField
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.util.crewTallyDatabase

/**
 * Full-screen add/edit form for a company. Field set (Phase 1 LOCKED): name (required),
 * contact person + phone (the shared [ContactSearchField] pair — "one component, two call
 * sites" with the clerk form), notes.
 *
 * Archive/delete affordance and add-button pattern both mirror
 * [com.vague.crewtally.ui.screen.clerk.ClerkFormScreen] exactly — see that file's KDoc for
 * why. Keeping the two forms structurally identical is what makes "one pattern for both
 * tabs" actually true rather than just similar-looking.
 */
@Composable
fun CompanyFormScreen(
    companyId: String?,
    onDone: () -> Unit,
    onShareReport: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CompanyFormViewModel = viewModel(
        factory = LocalContext.current.crewTallyDatabase().let { db ->
            CompanyFormViewModel.factory(db.companyDao(), db.projectDao(), companyId)
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
            text = stringResource(if (uiState.isEditing) R.string.company_form_title_edit else R.string.company_form_title_new),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )

        CrewTallyTextField(
            value = uiState.name,
            onValueChange = { viewModel.onEvent(CompanyFormEvent.NameChanged(it)) },
            label = stringResource(R.string.field_name),
            isError = attemptedSave && !uiState.isNameValid,
            supportingText = if (attemptedSave && !uiState.isNameValid) {
                stringResource(R.string.error_name_required)
            } else {
                null
            },
        )

        ContactSearchField(
            name = uiState.contactPerson,
            onNameChange = { viewModel.onEvent(CompanyFormEvent.ContactPersonChanged(it)) },
            phone = uiState.phone,
            onPhoneChange = { viewModel.onEvent(CompanyFormEvent.PhoneChanged(it)) },
            nameLabel = stringResource(R.string.field_contact_person),
            phoneLabel = stringResource(R.string.field_phone),
            namePlaceholder = stringResource(R.string.field_name_search_placeholder),
        )

        CrewTallyTextField(
            value = uiState.notes,
            onValueChange = { viewModel.onEvent(CompanyFormEvent.NotesChanged(it)) },
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
                    viewModel.onEvent(CompanyFormEvent.Save)
                } else {
                    attemptedSave = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (uiState.isEditing) {
            TextButton(
                onClick = { onShareReport(requireNotNull(companyId)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = CrewTallyTheme.dimens.minTarget),
            ) {
                Text(text = stringResource(R.string.report_share_company), style = MaterialTheme.typography.labelLarge)
            }

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
            title = stringResource(R.string.company_archive_confirm_title),
            body = stringResource(R.string.company_archive_confirm_body),
            confirmLabel = stringResource(R.string.action_archive),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                showArchiveConfirm = false
                viewModel.onEvent(CompanyFormEvent.Archive)
            },
            onDismiss = { showArchiveConfirm = false },
        )
    }
    if (showUnarchiveConfirm) {
        CrewTallyConfirmDialog(
            title = stringResource(R.string.company_unarchive_confirm_title),
            body = stringResource(R.string.company_unarchive_confirm_body),
            confirmLabel = stringResource(R.string.action_unarchive),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                showUnarchiveConfirm = false
                viewModel.onEvent(CompanyFormEvent.Unarchive)
            },
            onDismiss = { showUnarchiveConfirm = false },
        )
    }
    if (showDeleteConfirm) {
        CrewTallyConfirmDialog(
            title = stringResource(R.string.company_delete_confirm_title),
            body = stringResource(R.string.company_delete_confirm_body),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            isDestructive = true,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.onEvent(CompanyFormEvent.Delete)
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
