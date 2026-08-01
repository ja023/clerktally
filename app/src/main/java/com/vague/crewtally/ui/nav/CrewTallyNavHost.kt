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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vague.crewtally.ui.screen.AddRosterClerkScreen
import com.vague.crewtally.ui.screen.CreateProjectScreen
import com.vague.crewtally.ui.screen.EditProjectScreen
import com.vague.crewtally.ui.screen.EditRosterRateScreen
import com.vague.crewtally.ui.screen.MoreScreen
import com.vague.crewtally.ui.screen.PlaceholderScreen
import com.vague.crewtally.ui.screen.ProjectDetailScreen
import com.vague.crewtally.ui.screen.ProjectsScreen
import com.vague.crewtally.ui.screen.attendance.AttendanceDayScreen
import com.vague.crewtally.ui.screen.attendance.AttendanceExtrasScreen
import com.vague.crewtally.ui.screen.attendance.AttendanceRoutes
import com.vague.crewtally.ui.screen.attendance.AttendanceWalkInScreen
import com.vague.crewtally.ui.screen.clerk.ClerkFormScreen
import com.vague.crewtally.ui.screen.clerk.ClerkListScreen
import com.vague.crewtally.ui.screen.company.CompanyFormScreen
import com.vague.crewtally.ui.screen.company.CompanyListScreen
import com.vague.crewtally.ui.theme.CrewTallyTheme
import java.time.LocalDate

/** Route templates for the Phase 1 add/edit forms, nested under Clerks and More. */
private object CrewTallyRoutes {
    const val CLERK_NEW = "clerks/new"
    const val CLERK_EDIT = "clerks/{clerkId}/edit"
    const val CLERK_ID_ARG = "clerkId"

    const val COMPANIES = "companies"
    const val COMPANY_NEW = "companies/new"
    const val COMPANY_EDIT = "companies/{companyId}/edit"
    const val COMPANY_ID_ARG = "companyId"
}

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
                ProjectsScreen(navController = navController)
            }
            composable(CrewTallyDestination.Clerks.route) {
                ClerkListScreen(
                    onAddClerk = { navController.navigate(CrewTallyRoutes.CLERK_NEW) },
                    onEditClerk = { clerkId -> navController.navigate("clerks/$clerkId/edit") },
                )
            }
            composable(CrewTallyRoutes.CLERK_NEW) {
                ClerkFormScreen(clerkId = null, onDone = { navController.popBackStack() })
            }
            composable(
                route = CrewTallyRoutes.CLERK_EDIT,
                arguments = listOf(navArgument(CrewTallyRoutes.CLERK_ID_ARG) { type = NavType.StringType }),
            ) { backStackEntry ->
                val clerkId = backStackEntry.arguments?.getString(CrewTallyRoutes.CLERK_ID_ARG)
                ClerkFormScreen(clerkId = clerkId, onDone = { navController.popBackStack() })
            }
            composable(CrewTallyDestination.More.route) {
                MoreScreen(onCompaniesClick = { navController.navigate(CrewTallyRoutes.COMPANIES) })
            }
            composable(CrewTallyRoutes.COMPANIES) {
                CompanyListScreen(
                    onAddCompany = { navController.navigate(CrewTallyRoutes.COMPANY_NEW) },
                    onEditCompany = { companyId -> navController.navigate("companies/$companyId/edit") },
                )
            }
            composable(CrewTallyRoutes.COMPANY_NEW) {
                CompanyFormScreen(companyId = null, onDone = { navController.popBackStack() })
            }
            composable(
                route = CrewTallyRoutes.COMPANY_EDIT,
                arguments = listOf(navArgument(CrewTallyRoutes.COMPANY_ID_ARG) { type = NavType.StringType }),
            ) { backStackEntry ->
                val companyId = backStackEntry.arguments?.getString(CrewTallyRoutes.COMPANY_ID_ARG)
                CompanyFormScreen(companyId = companyId, onDone = { navController.popBackStack() })
            }

            // --- Phase 2: Projects + roster ------------------------------------------
            composable("project/create") {
                CreateProjectScreen(navController = navController)
            }
            composable(
                route = "project/{projectId}",
                arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
                ProjectDetailScreen(projectId = projectId, navController = navController)
            }
            composable(
                route = "project/{projectId}/edit",
                arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
                EditProjectScreen(projectId = projectId, navController = navController)
            }
            composable(
                route = "project/{projectId}/roster/add/{currency}",
                arguments = listOf(
                    navArgument("projectId") { type = NavType.StringType },
                    navArgument("currency") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
                val currency = backStackEntry.arguments?.getString("currency").orEmpty()
                AddRosterClerkScreen(projectId = projectId, currency = currency, navController = navController)
            }
            composable(
                route = "project/{projectId}/roster/{rosterEntryId}/edit/{clerkId}/{clerkName}/{rate}/{currency}",
                arguments = listOf(
                    navArgument("projectId") { type = NavType.StringType },
                    navArgument("rosterEntryId") { type = NavType.StringType },
                    navArgument("clerkId") { type = NavType.StringType },
                    navArgument("clerkName") { type = NavType.StringType },
                    navArgument("rate") { type = NavType.LongType },
                    navArgument("currency") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val arguments = backStackEntry.arguments
                EditRosterRateScreen(
                    rosterEntryId = arguments?.getString("rosterEntryId").orEmpty(),
                    projectId = arguments?.getString("projectId").orEmpty(),
                    clerkId = arguments?.getString("clerkId").orEmpty(),
                    clerkName = arguments?.getString("clerkName").orEmpty(),
                    rateMinorUnits = arguments?.getLong("rate") ?: 0L,
                    currency = arguments?.getString("currency").orEmpty(),
                    navController = navController,
                )
            }

            // --- Phase 3: Attendance daily loop --------------------------------------
            composable(
                route = AttendanceRoutes.DAY_TEMPLATE,
                arguments = listOf(
                    navArgument(AttendanceRoutes.PROJECT_ID) { type = NavType.StringType },
                    navArgument(AttendanceRoutes.EPOCH_DAY) { type = NavType.LongType },
                ),
            ) { backStackEntry ->
                val arguments = backStackEntry.arguments
                AttendanceDayScreen(
                    projectId = arguments?.getString(AttendanceRoutes.PROJECT_ID).orEmpty(),
                    initialDate = LocalDate.ofEpochDay(arguments?.getLong(AttendanceRoutes.EPOCH_DAY) ?: 0L),
                    navController = navController,
                )
            }
            composable(
                route = AttendanceRoutes.EXTRAS_TEMPLATE,
                arguments = listOf(
                    navArgument(AttendanceRoutes.PROJECT_ID) { type = NavType.StringType },
                    navArgument(AttendanceRoutes.EPOCH_DAY) { type = NavType.LongType },
                    navArgument(AttendanceRoutes.CLERK_ID) { type = NavType.StringType },
                    navArgument(AttendanceRoutes.CLERK_NAME) { type = NavType.StringType },
                    navArgument(AttendanceRoutes.RATE) { type = NavType.LongType },
                    navArgument(AttendanceRoutes.CURRENCY) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val arguments = backStackEntry.arguments
                AttendanceExtrasScreen(
                    projectId = arguments?.getString(AttendanceRoutes.PROJECT_ID).orEmpty(),
                    clerkId = arguments?.getString(AttendanceRoutes.CLERK_ID).orEmpty(),
                    date = LocalDate.ofEpochDay(arguments?.getLong(AttendanceRoutes.EPOCH_DAY) ?: 0L),
                    clerkName = arguments?.getString(AttendanceRoutes.CLERK_NAME).orEmpty(),
                    rateMinorUnits = arguments?.getLong(AttendanceRoutes.RATE) ?: 0L,
                    currency = arguments?.getString(AttendanceRoutes.CURRENCY).orEmpty(),
                    navController = navController,
                )
            }
            composable(
                route = AttendanceRoutes.WALK_IN_TEMPLATE,
                arguments = listOf(
                    navArgument(AttendanceRoutes.PROJECT_ID) { type = NavType.StringType },
                    navArgument(AttendanceRoutes.EPOCH_DAY) { type = NavType.LongType },
                    navArgument(AttendanceRoutes.CURRENCY) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val arguments = backStackEntry.arguments
                AttendanceWalkInScreen(
                    projectId = arguments?.getString(AttendanceRoutes.PROJECT_ID).orEmpty(),
                    date = LocalDate.ofEpochDay(arguments?.getLong(AttendanceRoutes.EPOCH_DAY) ?: 0L),
                    currency = arguments?.getString(AttendanceRoutes.CURRENCY).orEmpty(),
                    navController = navController,
                )
            }
        }
    }
}
