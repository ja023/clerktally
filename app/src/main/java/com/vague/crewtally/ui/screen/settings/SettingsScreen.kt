package com.vague.crewtally.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vague.crewtally.BuildConfig
import com.vague.crewtally.R
import com.vague.crewtally.backup.BackupPreferences
import com.vague.crewtally.ui.components.CrewTallyArchivedToggle
import com.vague.crewtally.ui.components.CrewTallyListRow
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.viewmodel.SettingsEvent
import com.vague.crewtally.ui.viewmodel.SettingsViewModel

/**
 * The Settings screen (LOCKED Phase 5): the backup-nudge toggle plus a read-only About section
 * (app name, version, a Privacy row). Nothing else in v1 (LOCKED). The toggle reuses
 * [CrewTallyArchivedToggle]'s row/switch pattern — it's a generic labelled-toggle row, not
 * specific to "archived", so it fits here without a new component.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenPrivacy: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val backupPreferences = remember { BackupPreferences(context) }
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(backupPreferences, BuildConfig.VERSION_NAME),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_screen_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(CrewTallyTheme.dimens.screenEdge),
            verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.sectionGap),
        ) {
            CrewTallyArchivedToggle(
                label = stringResource(R.string.settings_nudge_toggle_label),
                checked = state.nudgeEnabled,
                onCheckedChange = { viewModel.onEvent(SettingsEvent.NudgeToggled(it)) },
            )

            Column(verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd)) {
                Text(
                    text = stringResource(R.string.settings_about_heading),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_version_label, state.versionName),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CrewTallyListRow(
                    title = stringResource(R.string.settings_privacy_row),
                    onClick = onOpenPrivacy,
                )
            }
        }
    }
}
