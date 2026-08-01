package com.vague.crewtally.ui.screen.attendance

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vague.crewtally.CrewTallyApplication
import com.vague.crewtally.R
import com.vague.crewtally.data.local.RoomAttendanceWriter
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.components.CrewTallyConfirmDialog
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.viewmodel.AttendanceDayEvent
import com.vague.crewtally.ui.viewmodel.AttendanceDayViewModel
import com.vague.crewtally.util.CurrencyCodes
import java.time.LocalDate

/**
 * The attendance daily loop — the app's most-used surface. Roster clerks (plus any day-only
 * walk-ins) each get a three-state row that auto-saves on every tap. A "Mark all present"
 * button and a soft, non-blocking unmarked reminder sit above the list; a walk-in entry point
 * sits below it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceDayScreen(
    projectId: String,
    initialDate: LocalDate,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as CrewTallyApplication
    val database = application.database
    val viewModel: AttendanceDayViewModel = viewModel(
        factory = AttendanceDayViewModel.factory(
            projectId = projectId,
            initialDate = initialDate,
            projectDao = database.projectDao(),
            rosterEntryDao = database.rosterEntryDao(),
            attendanceEntryDao = database.attendanceEntryDao(),
            extraPayLineDao = database.extraPayLineDao(),
            attendanceWriter = RoomAttendanceWriter(database),
        ),
    )

    val date by viewModel.date.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val project by viewModel.project.collectAsStateWithLifecycle()
    val unmarkedCount by viewModel.unmarkedCount.collectAsStateWithLifecycle()
    val isBeforeStart by viewModel.isBeforeStartDate.collectAsStateWithLifecycle()
    val pendingUnmark by viewModel.pendingUnmark.collectAsStateWithLifecycle()

    val currencySymbol = CurrencyCodes.symbolFor(project?.currency.orEmpty())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.attendance_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(CrewTallyTheme.dimens.screenEdge),
            verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd),
        ) {
            item(key = "date-bar") {
                AttendanceDateBar(
                    date = date,
                    onPrev = { viewModel.onEvent(AttendanceDayEvent.PrevDay) },
                    onNext = { viewModel.onEvent(AttendanceDayEvent.NextDay) },
                    onDateSelected = { viewModel.onEvent(AttendanceDayEvent.DateSelected(it)) },
                )
            }

            if (isBeforeStart) {
                item(key = "before-start") {
                    BackfillWarning()
                }
            }

            item(key = "mark-all") {
                Column(verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceSm)) {
                    CrewTallyButton(
                        text = stringResource(R.string.attendance_mark_all_present),
                        onClick = { viewModel.onEvent(AttendanceDayEvent.MarkAllPresent) },
                        enabled = rows.any { !it.isWalkIn },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    UnmarkedReminder(unmarkedCount = unmarkedCount)
                }
            }

            if (rows.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.attendance_empty_roster),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(rows, key = { it.clerkId }) { row ->
                    AttendanceClerkRow(
                        row = row,
                        currencySymbol = currencySymbol,
                        onPresent = { viewModel.onEvent(AttendanceDayEvent.PresentTapped(row.clerkId)) },
                        onAbsent = { viewModel.onEvent(AttendanceDayEvent.AbsentTapped(row.clerkId)) },
                        onExtras = {
                            navController.navigate(
                                AttendanceRoutes.extras(projectId, date, row.clerkId, row.clerkName, row.rateMinorUnits, project?.currency.orEmpty()),
                            )
                        },
                    )
                }
            }

            item(key = "add-walk-in") {
                Spacer(Modifier.height(CrewTallyTheme.dimens.spaceSm))
                CrewTallyButton(
                    text = stringResource(R.string.attendance_add_walk_in),
                    onClick = {
                        navController.navigate(
                            AttendanceRoutes.walkIn(projectId, date, project?.currency.orEmpty()),
                        )
                    },
                    leadingIcon = Icons.Filled.PersonAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    val pending = pendingUnmark
    if (pending != null) {
        CrewTallyConfirmDialog(
            title = stringResource(R.string.attendance_unmark_confirm_title),
            body = stringResource(R.string.attendance_unmark_confirm_body, pending.clerkName),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { viewModel.onEvent(AttendanceDayEvent.ConfirmUnmark) },
            onDismiss = { viewModel.onEvent(AttendanceDayEvent.DismissUnmark) },
            isDestructive = true,
        )
    }
}

@Composable
private fun BackfillWarning(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = CrewTallyShape.card,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.attendance_before_start_warning),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(CrewTallyTheme.dimens.spaceLg)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun UnmarkedReminder(unmarkedCount: Int, modifier: Modifier = Modifier) {
    val text = if (unmarkedCount > 0) {
        pluralStringResource(R.plurals.attendance_unmarked_reminder, unmarkedCount, unmarkedCount)
    } else {
        stringResource(R.string.attendance_all_marked)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
