package dev.myalarm.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.myalarm.R
import dev.myalarm.alarm.ui.AlarmEditorScreen
import dev.myalarm.alarm.ui.AlarmListScreen
import dev.myalarm.history.ui.HistoryScreen
import dev.myalarm.history.ui.OccurrenceDetailScreen
import dev.myalarm.reliability.ui.ReliabilityScreen
import dev.myalarm.settings.ui.SettingsScreen

private data class Tab(val route: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun MyAlarmNavHost() {
    val navController = rememberNavController()
    val tabs =
        listOf(
            Tab("alarms", R.string.alarms, Icons.Default.Alarm),
            Tab("history", R.string.history, Icons.Default.History),
            Tab("settings", R.string.settings, Icons.Default.Settings),
        )
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        bottomBar = {
            if (route in tabs.map { it.route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(selected = route == tab.route, onClick = {
                            navController.navigate(tab.route) { launchSingleTop = true }
                        }, icon = { Icon(tab.icon, stringResource(tab.labelRes)) }, label = { Text(stringResource(tab.labelRes)) })
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = "alarms", modifier = Modifier.padding(padding)) {
            composable("alarms") {
                AlarmListScreen(onEdit = {
                    navController.navigate("alarmEditor?alarmId=$it")
                }, onReliability = { navController.navigate("reliability") })
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
            composable("history") { HistoryScreen(onOpen = { navController.navigate("occurrence/$it") }) }
            composable(
                "occurrence/{id}",
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.LongType
                    },
                ),
            ) { OccurrenceDetailScreen(onBack = { navController.popBackStack() }) }
            composable("settings") { SettingsScreen(onReliability = { navController.navigate("reliability") }) }
            composable("reliability") { ReliabilityScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
