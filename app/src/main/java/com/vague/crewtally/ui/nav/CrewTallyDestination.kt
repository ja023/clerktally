package com.vague.crewtally.ui.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.vague.crewtally.R

/**
 * The four top-level bottom-bar destinations, in bar order. This is the whole shallow,
 * always-visible navigation surface — no hidden gestures, no hamburger. Each destination
 * carries its route, a labelled icon, and a content description for TalkBack.
 */
enum class CrewTallyDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:StringRes val contentDescriptionRes: Int,
    val icon: ImageVector,
) {
    Home("home", R.string.nav_home, R.string.cd_nav_home, Icons.Filled.Home),
    Projects("projects", R.string.nav_projects, R.string.cd_nav_projects, Icons.Filled.Work),
    Clerks("clerks", R.string.nav_clerks, R.string.cd_nav_clerks, Icons.Filled.Groups),
    More("more", R.string.nav_more, R.string.cd_nav_more, Icons.Filled.MoreHoriz),
    ;

    companion object {
        val START: CrewTallyDestination = Home
    }
}
