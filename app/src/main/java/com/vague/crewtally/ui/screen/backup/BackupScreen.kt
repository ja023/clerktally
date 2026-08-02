package com.vague.crewtally.ui.screen.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vague.crewtally.BuildConfig
import com.vague.crewtally.CrewTallyApplication
import com.vague.crewtally.R
import com.vague.crewtally.backup.BackupCounts
import com.vague.crewtally.backup.BackupPreferences
import com.vague.crewtally.ui.components.CrewTallyAlertDialog
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.components.CrewTallyConfirmDialog
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.util.BackupFileReadOutcome
import com.vague.crewtally.ui.util.backupCountsSentence
import com.vague.crewtally.ui.util.readBackupFile
import com.vague.crewtally.ui.util.restoreFailureText
import com.vague.crewtally.ui.util.shareFile
import com.vague.crewtally.ui.viewmodel.BackupEvent
import com.vague.crewtally.ui.viewmodel.BackupViewModel
import com.vague.crewtally.ui.viewmodel.RestoreFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Backup screen (LOCKED Phase 5): export builds a whole-database JSON file and hands it to
 * the share sheet; restore picks a file via Storage Access Framework, validates it fully, shows
 * a destructive confirm naming exactly what will be replaced, then either succeeds (a
 * confirmation panel replaces the form and echoes what was imported — Jad's locked
 * confirmation-replaces-the-form preference) or fails with a specific, non-technical message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(navController: NavController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val application = context.applicationContext as CrewTallyApplication
    val backupPreferences = remember { BackupPreferences(context) }
    val viewModel: BackupViewModel = viewModel(
        factory = BackupViewModel.factory(
            backupExporter = application.backupExporter,
            fileWriter = application.reportFileWriter,
            restoreWriter = application.restoreWriter,
            backupPreferences = backupPreferences,
            appVersionName = BuildConfig.VERSION_NAME,
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val exportingDescription = stringResource(R.string.backup_exporting)
    val restoringDescription = stringResource(R.string.backup_restoring)
    val validatingDescription = stringResource(R.string.backup_validating_restore)
    val actionsEnabled = !state.isExporting && !state.isRestoring && !state.isValidatingRestore

    LaunchedEffect(Unit) {
        viewModel.shareRequests.collect { request -> context.shareFile(request.file, request.mimeType) }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val outcome = withContext(Dispatchers.IO) { readBackupFile(context, uri) }) {
                is BackupFileReadOutcome.Content -> viewModel.onEvent(BackupEvent.RestoreFilePicked(outcome.text))
                BackupFileReadOutcome.TooLarge ->
                    viewModel.onEvent(BackupEvent.RestoreFileRejected(RestoreFailure.FileTooLarge))
                BackupFileReadOutcome.Unreadable ->
                    viewModel.onEvent(BackupEvent.RestoreFileRejected(RestoreFailure.FileUnreadable))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_screen_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        val success = state.restoreSuccess
        if (success != null) {
            RestoreSuccessPanel(
                counts = success,
                onDone = { viewModel.onEvent(BackupEvent.DismissRestoreSuccess) },
                modifier = Modifier.fillMaxSize().padding(padding).padding(CrewTallyTheme.dimens.screenEdge),
            )
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(CrewTallyTheme.dimens.screenEdge),
                verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.sectionGap),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceSm)) {
                    CrewTallyButton(
                        text = stringResource(R.string.backup_export_action),
                        onClick = { viewModel.onEvent(BackupEvent.ExportRequested) },
                        enabled = actionsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.backup_export_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.isExporting) BackupProgressRow(exportingDescription)
                }

                Column(verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceSm)) {
                    CrewTallyButton(
                        text = stringResource(R.string.backup_restore_action),
                        onClick = { restoreLauncher.launch(arrayOf("application/json", "text/*")) },
                        enabled = actionsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.backup_restore_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.isValidatingRestore) BackupProgressRow(validatingDescription)
                    if (state.isRestoring) BackupProgressRow(restoringDescription)
                }
            }
        }
    }

    state.pendingRestore?.let { pending ->
        CrewTallyConfirmDialog(
            title = stringResource(R.string.backup_restore_confirm_title),
            body = stringResource(R.string.backup_restore_confirm_body, backupCountsSentence(pending.counts)),
            confirmLabel = stringResource(R.string.backup_restore_confirm_action),
            dismissLabel = stringResource(R.string.action_cancel),
            isDestructive = true,
            onConfirm = { viewModel.onEvent(BackupEvent.ConfirmRestore) },
            onDismiss = { viewModel.onEvent(BackupEvent.DismissRestoreConfirm) },
        )
    }

    state.restoreFailure?.let { failure ->
        CrewTallyAlertDialog(
            title = stringResource(R.string.backup_restore_error_title),
            body = restoreFailureText(failure),
            actionLabel = stringResource(R.string.action_ok),
            onDismiss = { viewModel.onEvent(BackupEvent.DismissRestoreFailure) },
        )
    }
}

@Composable
private fun BackupProgressRow(description: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = description
            },
    ) {
        CircularProgressIndicator(modifier = Modifier.height(CrewTallyTheme.dimens.iconLg))
        Spacer(Modifier.width(CrewTallyTheme.dimens.spaceSm))
        Text(text = description, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Confirmation-replaces-the-form (Jad's locked preference): echoes exactly what was imported.
 * Title and body sit in ONE merged semantics node (mirroring `OwedFigureCard`'s pattern) so
 * TalkBack reads the whole imported-counts sentence as a single announcement instead of two
 * separate, disconnected nodes.
 */
@Composable
private fun RestoreSuccessPanel(counts: BackupCounts, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.backup_restore_success_title)
    val body = stringResource(R.string.backup_restore_success_body, backupCountsSentence(counts))

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceLg),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceSm),
            modifier = Modifier.semantics(mergeDescendants = true) {
                heading()
                liveRegion = LiveRegionMode.Polite
                contentDescription = "$title. $body"
            },
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        CrewTallyButton(text = stringResource(R.string.backup_done_action), onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}
