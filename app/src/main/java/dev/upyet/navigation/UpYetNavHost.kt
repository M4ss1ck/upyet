package dev.upyet.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.upyet.alarm.ui.AlarmEditorScreen
import dev.upyet.history.ui.HistoryScreen
import dev.upyet.history.ui.OccurrenceDetailScreen
import dev.upyet.reliability.ui.ReliabilityScreen
import dev.upyet.settings.ui.DataSourcesScreen
import dev.upyet.settings.ui.SettingsScreen

/** Route names. The start destination is [TABS], so every launch lands on Alarms. */
internal object Routes {
    const val TABS = "tabs"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val RELIABILITY = "reliability"
    const val DATA_SOURCES = "data_sources"
}

@Composable
fun UpYetNavHost() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = Routes.TABS) {
        composable(Routes.TABS) {
            TabsScreen(
                onEdit = { navController.navigate("alarmEditor?alarmId=$it") },
                onHistory = { navController.navigate(Routes.HISTORY) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onReliability = { navController.navigate(Routes.RELIABILITY) },
            )
        }
        composable(
            "alarmEditor?alarmId={id}",
            arguments = listOf(
                navArgument("id") {
                    type = NavType.LongType
                    defaultValue = -1L
                    nullable =
                        false
                },
            ),
        ) { backStack ->
            AlarmEditorScreen(
                alarmId = backStack.arguments?.getLong("id")?.takeIf {
                    it >= 0
                },
                onDone = { navController.popBackStack() },
                onReliability = { navController.navigate(Routes.RELIABILITY) },
            )
        }
        composable(
            "occurrence/{id}",
            arguments = listOf(
                navArgument("id") {
                    type = NavType.LongType
                },
            ),
        ) { OccurrenceDetailScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.RELIABILITY) { ReliabilityScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.DATA_SOURCES) { DataSourcesScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onOpen = { navController.navigate("occurrence/$it") },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onReliability = { navController.navigate(Routes.RELIABILITY) },
                onDataSources = { navController.navigate(Routes.DATA_SOURCES) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
