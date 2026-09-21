package com.vague.crewtally.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vague.crewtally.R
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * The trailing document button on a clerk row that already taps through somewhere else (project
 * roster rows, Home's owed-clerks rows — LOCKED v1.2 "Access"): it opens the clerk PROFILE,
 * where both cross-project statements live, without changing the row's own tap target.
 *
 * It deliberately sits OUTSIDE [CrewTallyListRow] rather than in its trailing slot: the row
 * merges its descendants into one accessibility node, which would swallow this button for a
 * screen-reader user. As its own sibling it stays a separate [CrewTallyDimens.minTarget] (48dp)
 * focusable target with its own spoken name.
 *
 * @param projectName when non-null, names the project in the spoken description too (v1.2 a11y
 *   fix: Home's owed-clerks rows can show the SAME clerk owed on two projects back to back, which
 *   otherwise reads as two identically-named buttons to TalkBack). Project roster rows leave this
 *   null and keep the plain "Open clerk profile for <name>" wording, since a roster row is
 *   already scoped to one project.
 */
@Composable
fun CrewTallyClerkProfileButton(
    clerkName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    projectName: String? = null,
) {
    val description = if (projectName != null) {
        stringResource(R.string.cd_open_clerk_profile_for_project, clerkName, projectName)
    } else {
        stringResource(R.string.cd_open_clerk_profile, clerkName)
    }
    IconButton(
        onClick = onClick,
        modifier = modifier.size(CrewTallyTheme.dimens.minTarget),
    ) {
        Icon(
            imageVector = Icons.Filled.Description,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(CrewTallyTheme.dimens.iconMd),
        )
    }
}
