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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.vague.crewtally.R
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * The full-screen, scrollable privacy text (LOCKED Phase 5: viewable offline, no network call to
 * show it — every section is a plain string resource). Split into topic sections (data storage,
 * contacts, backups) each carrying its own heading, rather than one undifferentiated wall of
 * text — TalkBack's heading navigation can then jump straight to the topic a user cares about.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_screen_title), modifier = Modifier.semantics { heading() }) },
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
            PrivacySection(
                headingText = stringResource(R.string.privacy_section_storage_heading),
                body = stringResource(R.string.privacy_section_storage_body),
            )
            PrivacySection(
                headingText = stringResource(R.string.privacy_section_contacts_heading),
                body = stringResource(R.string.privacy_section_contacts_body),
            )
            PrivacySection(
                headingText = stringResource(R.string.privacy_section_backups_heading),
                body = stringResource(R.string.privacy_section_backups_body),
            )
        }
    }
}

@Composable
private fun PrivacySection(headingText: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceSm)) {
        Text(
            text = headingText,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
