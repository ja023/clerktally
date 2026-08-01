package com.vague.crewtally.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vague.crewtally.ui.screen.MoreScreen
import com.vague.crewtally.ui.screen.PlaceholderScreen
import com.vague.crewtally.ui.theme.CrewTallyTheme

/**
 * The app shell: a bottom navigation bar over a [NavHost]. The four destinations are
 * always visible with large labelled icons (labels never hidden), each a full 56dp+ target.
 * This is the whole navigation surface — deliberately flat and obvious for the primary user.
 */
@Composable
fun CrewTallyNavHost() {
    val navController = rememberNavController()
    val destinations = CrewTallyDestination.entries

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination

            NavigationBar {
                destinations.forEach { destination ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == destination.route } == true
                    val label = stringResource(destination.labelRes)
                    val cd = stringResource(destination.contentDescriptionRes)

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(destination.route) {
                                    // Single top-level back stack: tapping a tab pops back to
                                    // the graph's start rather than piling destinations up.
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null, // item is labelled as a whole below
                                modifier = Modifier.size(CrewTallyTheme.dimens.iconLg),
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.clearAndSetSemantics { contentDescription = cd },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = CrewTallyDestination.START.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(CrewTallyDestination.Home.route) {
                PlaceholderScreen(title = stringResource(CrewTallyDestination.Home.labelRes))
            }
            composable(CrewTallyDestination.Projects.route) {
                PlaceholderScreen(title = stringResource(CrewTallyDestination.Projects.labelRes))
            }
            composable(CrewTallyDestination.Clerks.route) {
                PlaceholderScreen(title = stringResource(CrewTallyDestination.Clerks.labelRes))
            }
            composable(CrewTallyDestination.More.route) {
                MoreScreen()
            }
        }
    }
}
