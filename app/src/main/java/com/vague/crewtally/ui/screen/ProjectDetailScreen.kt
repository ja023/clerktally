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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vague.crewtally.CrewTallyApplication
import com.vague.crewtally.R
import com.vague.crewtally.data.local.ProjectStatus
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.components.CrewTallyConfirmDialog
import com.vague.crewtally.ui.screen.attendance.AttendanceRoutes
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.viewmodel.ProjectDetailViewModel
import com.vague.crewtally.util.CurrencyCodes
import java.time.LocalDate

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
            database.attendanceEntryDao(),
        ),
    )
    val project by viewModel.project.collectAsStateWithLifecycle()
    val companyName by viewModel.companyName.collectAsStateWithLifecycle()
    val roster by viewModel.roster.collectAsStateWithLifecycle()
    val attendanceDays by viewModel.attendanceDays.collectAsStateWithLifecycle()

    var showMarkCompletedConfirm by remember { mutableStateOf(false) }
    var showReopenConfirm by remember { mutableStateOf(false) }
    val currentProject = project
    val loadingDescription = stringResource(R.string.cd_loading)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.name.orEmpty(), modifier = Modifier.semantics { heading() }) },
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
                CircularProgressIndicator(
                    modifier = Modifier.semantics { contentDescription = loadingDescription },
                )
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

                // The daily primary action. Attendance is only taken on active projects.
                if (currentProject.status == ProjectStatus.ACTIVE) {
                    CrewTallyButton(
                        text = stringResource(R.string.project_take_attendance),
                        onClick = {
                            navController.navigate(AttendanceRoutes.day(projectId, LocalDate.now()))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(CrewTallyTheme.dimens.sectionGap))
                }

                ProjectRosterSection(
                    roster = roster,
                    currencySymbol = CurrencyCodes.symbolFor(currentProject.currency),
                    onAddClerk = {
                        val encodedCurrency = Uri.encode(currentProject.currency)
                        navController.navigate("project/$projectId/roster/add/$encodedCurrency")
                    },
                    onRowClick = { row ->
                        val encodedName = Uri.encode(row.clerkName)
                        val encodedCurrency = Uri.encode(currentProject.currency)
                        navController.navigate(
                            "project/$projectId/roster/${row.entry.id}/edit/${row.entry.clerkId}/" +
                                "$encodedName/${row.entry.dailyRate}/$encodedCurrency",
                        )
                    },
                )

                Spacer(Modifier.height(CrewTallyTheme.dimens.sectionGap))

                AttendanceHistorySection(
                    days = attendanceDays,
                    currencySymbol = CurrencyCodes.symbolFor(currentProject.currency),
                    onDayClick = { day -> navController.navigate(AttendanceRoutes.day(projectId, day)) },
                )

                Spacer(Modifier.height(CrewTallyTheme.dimens.sectionGap))

                // Status change is a secondary action (de-emphasized) so "Take attendance"
                // stays the screen's single primary action for active projects.
                when (currentProject.status) {
                    ProjectStatus.ACTIVE -> TextButton(onClick = { showMarkCompletedConfirm = true }) {
                        Text(stringResource(R.string.project_mark_completed))
                    }
                    ProjectStatus.COMPLETED -> TextButton(onClick = { showReopenConfirm = true }) {
                        Text(stringResource(R.string.project_reopen))
                    }
                    ProjectStatus.ARCHIVED -> Unit
                }
            }
        }

        if (showMarkCompletedConfirm && currentProject != null) {
            CrewTallyConfirmDialog(
                title = stringResource(R.string.project_mark_completed_confirm_title),
                body = stringResource(R.string.project_mark_completed_confirm_body),
                confirmLabel = stringResource(R.string.action_confirm),
                dismissLabel = stringResource(R.string.action_cancel),
                onConfirm = {
                    viewModel.markCompleted(currentProject)
                    showMarkCompletedConfirm = false
                },
                onDismiss = { showMarkCompletedConfirm = false },
                isDestructive = false,
            )
        }

        if (showReopenConfirm && currentProject != null) {
            CrewTallyConfirmDialog(
                title = stringResource(R.string.project_reopen_confirm_title),
                body = stringResource(R.string.project_reopen_confirm_body),
                confirmLabel = stringResource(R.string.action_confirm),
                dismissLabel = stringResource(R.string.action_cancel),
                onConfirm = {
                    viewModel.reopen(currentProject)
                    showReopenConfirm = false
                },
                onDismiss = { showReopenConfirm = false },
                isDestructive = false,
            )
        }
    }
}
