package com.vague.crewtally.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vague.crewtally.R
import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.data.local.ProjectStatus
import com.vague.crewtally.ui.theme.CrewTallyShape
import com.vague.crewtally.ui.theme.CrewTallyTheme
import java.time.format.DateTimeFormatter

private val DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/** The project detail screen's read-only details card: company, currency, location, dates, notes, status. */
@Composable
fun ProjectDetailInfoSection(
    project: ProjectEntity,
    companyName: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = CrewTallyShape.card,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(CrewTallyTheme.dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceMd),
        ) {
            DetailRow(stringResource(R.string.project_field_company), companyName)
            DetailRow(stringResource(R.string.project_field_currency), project.currency)
            if (project.location.isNotBlank()) {
                DetailRow(stringResource(R.string.project_field_location), project.location)
            }
            DetailRow(stringResource(R.string.project_detail_start_date), project.startDate.format(DateFormat))
            project.endDate?.let { DetailRow(stringResource(R.string.project_detail_end_date), it.format(DateFormat)) }
            DetailRow(stringResource(R.string.project_detail_status), statusLabel(project.status))
            if (project.notes.isNotBlank()) {
                DetailRow(stringResource(R.string.project_field_notes), project.notes)
            }
        }
    }
}

@Composable
private fun statusLabel(status: ProjectStatus): String = when (status) {
    ProjectStatus.ACTIVE -> stringResource(R.string.project_status_active)
    ProjectStatus.COMPLETED -> stringResource(R.string.project_status_completed)
    ProjectStatus.ARCHIVED -> stringResource(R.string.project_status_archived)
}

@Composable
private fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
