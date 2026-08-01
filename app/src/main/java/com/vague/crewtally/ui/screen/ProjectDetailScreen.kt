package com.vague.crewtally.ui.screen

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vague.crewtally.CrewTallyApplication
import com.vague.crewtally.R
import com.vague.crewtally.data.local.ProjectStatus
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.viewmodel.ProjectDetailViewModel
import com.vague.crewtally.util.CurrencyCodes

/**
 * View-first project detail: read-only details + roster, with edit/status/roster actions
 * reached from here (LOCKED Phase 2 decision — this screen is not itself a form).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(projectId: String, navController: NavController, modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as CrewTallyApplication
    val database = application.database
    val viewModel: ProjectDetailViewModel = viewModel(
        factory = ProjectDetailViewModel.factory(
            projectId,
            database.projectDao(),
            database.companyDao(),
            database.rosterEntryDao(),
        ),
    )
    val project by viewModel.project.collectAsStateWithLifecycle()
    val companyName by viewModel.companyName.collectAsStateWithLifecycle()
    val roster by viewModel.roster.collectAsStateWithLifecycle()

    var showMarkCompletedConfirm by remember { mutableStateOf(false) }
    var showReopenConfirm by remember { mutableStateOf(false) }
    val currentProject = project

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (project != null) {
                        IconButton(onClick = { navController.navigate("project/$projectId/edit") }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (currentProject == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(CrewTallyTheme.dimens.screenEdge),
            ) {
                ProjectDetailInfoSection(project = currentProject, companyName = companyName)
                Spacer(Modifier.height(CrewTallyTheme.dimens.sectionGap))

                when (currentProject.status) {
                    ProjectStatus.ACTIVE -> CrewTallyButton(
                        text = stringResource(R.string.project_mark_completed),
                        onClick = { showMarkCompletedConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ProjectStatus.COMPLETED -> CrewTallyButton(
                        text = stringResource(R.string.project_reopen),
                        onClick = { showReopenConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ProjectStatus.ARCHIVED -> Unit
                }

                Spacer(Modifier.height(CrewTallyTheme.dimens.sectionGap))

                ProjectRosterSection(
                    roster = roster,
                    currencySymbol = CurrencyCodes.symbolFor(currentProject.currency),
                    onAddClerk = {
                        navController.navigate("project/$projectId/roster/add/${currentProject.currency}")
                    },
                    onRowClick = { row ->
                        val encodedName = Uri.encode(row.clerkName)
                        navController.navigate(
                            "project/$projectId/roster/${row.entry.id}/edit/${row.entry.clerkId}/" +
                                "$encodedName/${row.entry.dailyRate}/${currentProject.currency}",
                        )
                    },
                )
            }
        }

        if (showMarkCompletedConfirm && currentProject != null) {
            AlertDialog(
                onDismissRequest = { showMarkCompletedConfirm = false },
                title = { Text(stringResource(R.string.project_mark_completed_confirm_title)) },
                text = { Text(stringResource(R.string.project_mark_completed_confirm_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.markCompleted(currentProject)
                        showMarkCompletedConfirm = false
                    }) { Text(stringResource(R.string.action_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showMarkCompletedConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }

        if (showReopenConfirm && currentProject != null) {
            AlertDialog(
                onDismissRequest = { showReopenConfirm = false },
                title = { Text(stringResource(R.string.project_reopen_confirm_title)) },
                text = { Text(stringResource(R.string.project_reopen_confirm_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.reopen(currentProject)
                        showReopenConfirm = false
                    }) { Text(stringResource(R.string.action_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showReopenConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
    }
}
