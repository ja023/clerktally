package com.vague.crewtally.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.vague.crewtally.R
import com.vague.crewtally.backup.BackupNudge
import com.vague.crewtally.backup.BackupPreferences
import com.vague.crewtally.ui.components.CrewTallyListRow
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * The "More" hub. Companies, Backup, and Settings are all live rows now (Phase 5 brings the
 * last two off the disabled-placeholder state). A gentle, dismissible banner nudges toward a
 * backup when [BackupNudge.shouldShow] says it's been too long — see that object's KDoc for the
 * "reappears next lapse" rule dismissing it follows.
 */
@Composable
fun MoreScreen(
    onCompaniesClick: () -> Unit,
    onBackupClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val backupPreferences = remember { BackupPreferences(context) }
    var showNudge by remember { mutableStateOf(false) }

    // Re-evaluated on every fresh entry into this screen (a new Composition each time the More
    // tab is (re)selected), so exporting a backup on the Backup screen and returning here makes
    // the banner disappear without needing a persistent, reactively-observed ViewModel for what
    // is otherwise a one-shot SharedPreferences snapshot.
    LaunchedEffect(Unit) {
        showNudge = BackupNudge.shouldShow(
            lastExportEpochMillis = backupPreferences.lastExportEpochMillis,
            nowEpochMillis = System.currentTimeMillis(),
            nudgeEnabled = backupPreferences.nudgeEnabled,
            dismissedForReference = backupPreferences.dismissedForReference,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = CrewTallyTheme.dimens.screenEdge,
                vertical = CrewTallyTheme.dimens.spaceXl,
            ),
        verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd),
    ) {
        Text(
            text = stringResource(R.string.nav_more),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )

        if (showNudge) {
            BackupNudgeBanner(
                onDismiss = {
                    backupPreferences.dismissedForReference =
                        backupPreferences.lastExportEpochMillis ?: BackupNudge.NEVER_EXPORTED_REFERENCE
                    showNudge = false
                },
                onBackUpNow = onBackupClick,
            )
        }

        val companies = stringResource(R.string.more_companies)
        CrewTallyListRow(
            title = companies,
            leadingIcon = Icons.Filled.Business,
            onClick = onCompaniesClick,
            contentDescription = companies,
        )
        CrewTallyListRow(
            title = stringResource(R.string.more_backup),
            leadingIcon = Icons.Filled.Backup,
            onClick = onBackupClick,
        )
        CrewTallyListRow(
            title = stringResource(R.string.more_settings),
            leadingIcon = Icons.Filled.Settings,
            onClick = onSettingsClick,
        )
    }
}

@Composable
private fun BackupNudgeBanner(onDismiss: () -> Unit, onBackUpNow: () -> Unit, modifier: Modifier = Modifier) {
    val message = stringResource(R.string.backup_nudge_message)
    val dismissDescription = stringResource(R.string.backup_nudge_dismiss)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CrewTallyShape.card,
        tonalElevation = CrewTallyTheme.dimens.elevationCard,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(CrewTallyTheme.dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceSm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = dismissDescription)
                }
            }
            TextButton(onClick = onBackUpNow, modifier = Modifier.heightIn(min = CrewTallyTheme.dimens.minTarget)) {
                Text(text = stringResource(R.string.backup_nudge_action), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
