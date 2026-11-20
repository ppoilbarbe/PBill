package net.cardolan.pbill.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.cardolan.pbill.data.ExpenseKind
import net.cardolan.pbill.data.Repository
import net.cardolan.pbill.data.Settings

private const val ROUTE_GROUPS = "groups"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_ABOUT = "about"
private const val ROUTE_GROUP = "group/{groupId}"
private const val ROUTE_EXPENSE = "group/{groupId}/expense?expenseId={expenseId}&kind={kind}"

@Composable
fun PBillNavHost(repository: Repository, settings: Settings) {
    val navController = rememberNavController()

    // Reopen the last tab once per launch (not on configuration changes), if it still exists
    var launchHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (launchHandled) return@LaunchedEffect
        launchHandled = true
        val lastTab = settings.lastTabAtLaunch
        if (settings.reopenLastTab.value && lastTab != null && repository.getGroup(lastTab) != null) {
            navController.navigate("group/$lastTab")
        }
    }

    NavHost(navController, startDestination = ROUTE_GROUPS) {
        composable(ROUTE_GROUPS) {
            LaunchedEffect(Unit) { settings.setLastTab(null) }
            GroupsScreen(
                repository = repository,
                settings = settings,
                onOpenGroup = { navController.navigate("group/$it") },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(settings, onBack = { navController.popBackStack() })
        }
        composable(ROUTE_ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_GROUP) { entry ->
            val groupId = entry.arguments?.getString("groupId").orEmpty()
            LaunchedEffect(groupId) { settings.setLastTab(groupId) }
            GroupScreen(
                repository = repository,
                groupId = groupId,
                onBack = { navController.popBackStack(ROUTE_GROUPS, inclusive = false) },
                onEditExpense = { expenseId, kind ->
                    val query = if (expenseId != null) "expenseId=$expenseId&kind=$kind" else "kind=$kind"
                    navController.navigate("group/$groupId/expense?$query")
                },
            )
        }
        composable(
            ROUTE_EXPENSE,
            arguments = listOf(
                navArgument("expenseId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("kind") {
                    type = NavType.StringType
                    defaultValue = ExpenseKind.EXPENSE
                },
            ),
        ) { entry ->
            val args = entry.arguments
            ExpenseEditScreen(
                repository = repository,
                groupId = args?.getString("groupId").orEmpty(),
                expenseId = args?.getString("expenseId"),
                kind = args?.getString("kind") ?: ExpenseKind.EXPENSE,
                onDone = { navController.popBackStack() },
            )
        }
    }
}
