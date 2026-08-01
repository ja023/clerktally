package com.vague.crewtally.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vague.crewtally.CrewTallyApplication
import com.vague.crewtally.R
import com.vague.crewtally.data.local.ProjectSummary
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.components.CrewTallyListRow
import com.vague.crewtally.ui.components.CrewTallySegmentedControl
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.viewmodel.ProjectSegment
import com.vague.crewtally.ui.viewmodel.ProjectsViewModel

/**
 * The Projects tab: a big Active/Completed segmented control over a list of projects, each
 * row showing name, company, and roster size (LOCKED Phase 2 decision). The add-project
 * action is a full-width primary [CrewTallyButton] pinned below the list — the app's
 * established primary-action pattern (set by [CrewTallyButton]'s own doc), not a new FAB
 * pattern this phase would otherwise have to introduce.
 */
@Composable
fun ProjectsScreen(navController: NavController, modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as CrewTallyApplication
    val viewModel: ProjectsViewModel = viewModel(
        factory = ProjectsViewModel.factory(application.database.projectDao()),
    )
    val activeProjects by viewModel.activeProjects.collectAsStateWithLifecycle()
    val completedProjects by viewModel.completedProjects.collectAsStateWithLifecycle()
    var segment by rememberSaveable { mutableStateOf(ProjectSegment.ACTIVE) }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.nav_projects),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(
                horizontal = CrewTallyTheme.dimens.screenEdge,
                vertical = CrewTallyTheme.dimens.spaceLg,
            ),
        )
        CrewTallySegmentedControl(
            options = listOf(
                ProjectSegment.ACTIVE to stringResource(R.string.projects_segment_active),
                ProjectSegment.COMPLETED to stringResource(R.string.projects_segment_completed),
            ),
            selected = segment,
            onSelect = { segment = it },
            modifier = Modifier.padding(horizontal = CrewTallyTheme.dimens.screenEdge),
        )

        val projects = if (segment == ProjectSegment.ACTIVE) activeProjects else completedProjects
        if (projects.isEmpty()) {
            ProjectsEmptyState(
                segment = segment,
                onAddProject = { navController.navigate("project/create") },
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    horizontal = CrewTallyTheme.dimens.screenEdge,
                    vertical = CrewTallyTheme.dimens.spaceLg,
                ),
                verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd),
            ) {
                items(projects, key = { it.project.id }) { summary ->
                    ProjectRow(summary = summary, onClick = { navController.navigate("project/${summary.project.id}") })
                }
            }
        }

        CrewTallyButton(
            text = stringResource(R.string.projects_add),
            leadingIcon = Icons.Filled.Add,
            onClick = { navController.navigate("project/create") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(CrewTallyTheme.dimens.screenEdge),
        )
    }
}

@Composable
private fun ProjectRow(summary: ProjectSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val rosterText = pluralStringResource(R.plurals.roster_size, summary.rosterSize, summary.rosterSize)
    val subtitle = if (summary.companyName.isEmpty()) rosterText else "${summary.companyName} · $rosterText"
    CrewTallyListRow(
        title = summary.project.name,
        subtitle = subtitle,
        onClick = onClick,
        contentDescription = "${summary.project.name}. $subtitle",
        modifier = modifier,
    )
}

@Composable
private fun ProjectsEmptyState(
    segment: ProjectSegment,
    onAddProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(CrewTallyTheme.dimens.screenEdge), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (segment == ProjectSegment.ACTIVE) {
                    stringResource(R.string.projects_empty_active)
                } else {
                    stringResource(R.string.projects_empty_completed)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (segment == ProjectSegment.ACTIVE) {
                Spacer(Modifier.height(CrewTallyTheme.dimens.spaceLg))
                CrewTallyButton(text = stringResource(R.string.projects_empty_cta), onClick = onAddProject)
            }
        }
    }
}
