package dev.upyet.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/**
 * [destination] is a one-shot request from the trampoline (ADR 0007); [onDestinationConsumed] clears it once applied,
 * so a recomposition or rotation never replays it.
 */
@Composable
fun UpYetNavHost(destination: LaunchDestination? = null, onDestinationConsumed: () -> Unit = {}) {
    val navController = rememberNavController()
    var requestedTab by remember { mutableStateOf<ClockTab?>(null) }
    LaunchedEffect(destination) {
        when (destination) {
            null -> return@LaunchedEffect

            LaunchDestination.Alarms, LaunchDestination.Timers -> {
                navController.popBackStack(Routes.TABS, inclusive = false)
                requestedTab = if (destination == LaunchDestination.Alarms) ClockTab.ALARMS else ClockTab.TIMER
            }

            LaunchDestination.Reliability -> navController.navigate(Routes.RELIABILITY)

            is LaunchDestination.AlarmEditor ->
                navController.navigate("alarmEditor?alarmId=-1" + destination.prefill?.let { "&prefill=$it" }.orEmpty())
        }
        onDestinationConsumed()
    }
    NavHost(navController, startDestination = Routes.TABS) {
        composable(Routes.TABS) {
            TabsScreen(
                requestedTab = requestedTab,
                onTabRequestConsumed = { requestedTab = null },
                onEdit = { navController.navigate("alarmEditor?alarmId=$it") },
                onHistory = { navController.navigate(Routes.HISTORY) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onReliability = { navController.navigate(Routes.RELIABILITY) },
            )
        }
        composable(
            "alarmEditor?alarmId={id}&prefill={prefill}",
            arguments = listOf(
                navArgument("id") {
                    type = NavType.LongType
                    defaultValue = -1L
                    nullable =
                        false
                },
                navArgument("prefill") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
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
