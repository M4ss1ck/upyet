package dev.upyet.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.upyet.alarm.ui.AlarmEditorScreen
import dev.upyet.history.ui.OccurrenceDetailScreen
import dev.upyet.reliability.ui.ReliabilityScreen

@Composable
fun UpYetNavHost() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "tabs") {
        composable("tabs") {
            TabsScreen(
                onEdit = { navController.navigate("alarmEditor?alarmId=$it") },
                onOpenOccurrence = { navController.navigate("occurrence/$it") },
                onReliability = { navController.navigate("reliability") },
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
                onReliability = { navController.navigate("reliability") },
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
        composable("reliability") { ReliabilityScreen(onBack = { navController.popBackStack() }) }
    }
}
