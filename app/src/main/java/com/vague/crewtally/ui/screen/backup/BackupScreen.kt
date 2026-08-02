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
import com.vague.crewtally.ui.util.restoreFailureText
import com.vague.crewtally.ui.util.shareFile
import com.vague.crewtally.ui.viewmodel.BackupEvent
import com.vague.crewtally.ui.viewmodel.BackupViewModel
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
    val database = application.database
    val backupPreferences = remember { BackupPreferences(context) }
    val viewModel: BackupViewModel = viewModel(
        factory = BackupViewModel.factory(
            companyDao = database.companyDao(),
            clerkDao = database.clerkDao(),
            projectDao = database.projectDao(),
            rosterEntryDao = database.rosterEntryDao(),
            attendanceEntryDao = database.attendanceEntryDao(),
            extraPayLineDao = database.extraPayLineDao(),
            paymentDao = database.paymentDao(),
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

    LaunchedEffect(Unit) {
        viewModel.shareRequests.collect { request -> context.shareFile(request.file, request.mimeType) }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val content = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull().orEmpty()
            }
            viewModel.onEvent(BackupEvent.RestoreFilePicked(content))
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
                        enabled = !state.isExporting,
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
                        onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                        enabled = !state.isRestoring,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.backup_restore_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.isRestoring) BackupProgressRow(restoringDescription)
                }
            }
        }
    }

    state.pendingRestore?.let { pending ->
        CrewTallyConfirmDialog(
            title = stringResource(R.string.backup_restore_confirm_title),
            body = stringResource(
                R.string.backup_restore_confirm_body,
                pending.counts.companies,
                pending.counts.clerks,
                pending.counts.projects,
                pending.counts.rosterEntries,
                pending.counts.attendanceEntries,
                pending.counts.extraPayLines,
                pending.counts.payments,
            ),
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

/** Confirmation-replaces-the-form (Jad's locked preference): echoes exactly what was imported. */
@Composable
private fun RestoreSuccessPanel(counts: BackupCounts, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceLg),
    ) {
        Text(
            text = stringResource(R.string.backup_restore_success_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(
            text = stringResource(
                R.string.backup_restore_success_body,
                counts.companies,
                counts.clerks,
                counts.projects,
                counts.rosterEntries,
                counts.attendanceEntries,
                counts.extraPayLines,
                counts.payments,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        CrewTallyButton(text = stringResource(R.string.backup_done_action), onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}
