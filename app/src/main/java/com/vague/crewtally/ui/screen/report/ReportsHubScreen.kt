package com.vague.crewtally.ui.screen.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavController
import com.vague.crewtally.R
import com.vague.crewtally.ui.components.CrewTallyBrandMark
import com.vague.crewtally.ui.components.CrewTallyListRow
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * The v1.1 Reports hub, reached from More (LOCKED: "new 'Reports' entry on the More tab"). Two
 * big rows lead to a picker (project or company) and then that picker's share screen. The
 * existing clerk statement stays where it is (the clerk balance screen) and is deliberately NOT
 * duplicated here (LOCKED).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsHubScreen(
    onProjectStatementClick: () -> Unit,
    onCompanyStatementClick: () -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // The brand mark heads the reports surface (LOCKED v1.2), matching the mark
                    // printed at the top of every PDF statement reached from here. Decorative:
                    // the title text beside it already names the screen.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceSm),
                        modifier = Modifier.semantics(mergeDescendants = true) { heading() },
                    ) {
                        CrewTallyBrandMark()
                        Text(stringResource(R.string.reports_hub_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
                .padding(CrewTallyTheme.dimens.screenEdge),
            verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd),
        ) {
            CrewTallyListRow(
                title = stringResource(R.string.reports_project_statement),
                leadingIcon = Icons.Filled.Description,
                onClick = onProjectStatementClick,
            )
            CrewTallyListRow(
                title = stringResource(R.string.reports_company_statement),
                leadingIcon = Icons.Filled.Business,
                onClick = onCompanyStatementClick,
            )
        }
    }
}
