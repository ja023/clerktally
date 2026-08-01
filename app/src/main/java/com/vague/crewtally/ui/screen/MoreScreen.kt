package com.vague.crewtally.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vague.crewtally.R
import com.vague.crewtally.ui.components.CrewTallyListRow
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * The "More" hub. In Phase 0 it lists its three future sections — Companies, Backup,
 * Settings — as clearly DISABLED rows (no onClick), each with a hint that it arrives in a
 * later update. This keeps the shallow navigation honest: the user can see everything the
 * app will hold without any of it pretending to work yet.
 */
@Composable
fun MoreScreen(modifier: Modifier = Modifier) {
    val disabledHint = stringResource(R.string.more_row_disabled_hint)

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
        )

        val companies = stringResource(R.string.more_companies)
        val backup = stringResource(R.string.more_backup)
        val settings = stringResource(R.string.more_settings)

        CrewTallyListRow(
            title = companies,
            subtitle = disabledHint,
            leadingIcon = Icons.Filled.Business,
            onClick = null,
            enabled = false,
            contentDescription = "$companies. $disabledHint",
        )
        CrewTallyListRow(
            title = backup,
            subtitle = disabledHint,
            leadingIcon = Icons.Filled.Backup,
            onClick = null,
            enabled = false,
            contentDescription = "$backup. $disabledHint",
        )
        CrewTallyListRow(
            title = settings,
            subtitle = disabledHint,
            leadingIcon = Icons.Filled.Settings,
            onClick = null,
            enabled = false,
            contentDescription = "$settings. $disabledHint",
        )
    }
}
