package dev.upyet.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.upyet.R
import dev.upyet.alarm.ui.AlarmListScreen
import dev.upyet.core.ui.components.ActionNeededBadge
import dev.upyet.core.ui.components.TabHeadline
import dev.upyet.stopwatch.ui.StopwatchScreen
import dev.upyet.timer.ui.TimerScreen
import kotlinx.coroutines.launch

/** The four Clock tools, in the order the bottom bar shows them. Alarms is the launch tab. */
internal enum class ClockTab(@StringRes val labelRes: Int, val icon: ImageVector) {
    ALARMS(R.string.alarms, Icons.Default.Alarm),
    WORLD_CLOCK(R.string.world_clock, Icons.Default.Public),
    TIMER(R.string.timer, Icons.Default.HourglassEmpty),
    STOPWATCH(R.string.stopwatch, Icons.Default.Timer),
}

/**
 * The shell around the four Clock tools. The reliability summary is re-read on every resume: the user may fix a
 * permission in system settings and come back, and the dot on the menu button must not keep naming a problem
 * that is already gone.
 */
@Composable
fun TabsScreen(
    onEdit: (Long) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onReliability: () -> Unit,
    viewModel: ShellViewModel = hiltViewModel(),
) {
    val summary by viewModel.reliability.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    ClockShell(
        actionNeeded = !summary.allGood,
        onHistory = onHistory,
        onSettings = onSettings,
        onReliability = onReliability,
    ) { tab ->
        when (tab) {
            ClockTab.ALARMS -> AlarmListScreen(onEdit = onEdit, onReliability = onReliability)

            // Listed one by one so each tool's slice gets a compiler prompt to replace its placeholder.
            ClockTab.TIMER -> TimerScreen()

            ClockTab.WORLD_CLOCK -> ClockToolPlaceholder()

            ClockTab.STOPWATCH -> StopwatchScreen()
        }
    }
}

/**
 * Bottom tabs, a per-tab headline with the menu button, and a drawer for the screens that are not tabs. The
 * tabs are tap-only: a horizontal swipe does not change tab, so a swipe inside a tab's content is never a tab
 * change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClockShell(
    actionNeeded: Boolean,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onReliability: () -> Unit,
    tabContent: @Composable (ClockTab) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(ClockTab.ALARMS) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // One handler for the whole shell. Back closes an open drawer, then returns to Alarms from another tab. On
    // Alarms with the drawer closed it is disabled, so back leaves the app the way the platform expects.
    BackHandler(enabled = drawerState.isOpen || tab != ClockTab.ALARMS) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            tab = ClockTab.ALARMS
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader()
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                DrawerAction(
                    title = stringResource(R.string.history),
                    icon = Icons.Default.History,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onHistory()
                    },
                )
                DrawerAction(
                    title = stringResource(R.string.settings),
                    icon = Icons.Default.Settings,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onSettings()
                    },
                )
                DrawerAction(
                    title = stringResource(R.string.reliability),
                    icon = Icons.Default.Warning,
                    actionNeeded = actionNeeded,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onReliability()
                    },
                )
            }
        },
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    ClockTab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = { Icon(entry.icon, contentDescription = null) },
                            label = { Text(stringResource(entry.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            // Consumed so a tab's own Scaffold (Alarms has one for its FAB) does not pad the status bar a second time.
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                TabHeadline(
                    title = stringResource(tab.labelRes),
                    actionNeeded = actionNeeded,
                    onMenu = { scope.launch { drawerState.open() } },
                )
                Box(Modifier.weight(1f)) { tabContent(tab) }
            }
        }
    }
}

/** Shown in the three Clock tools that are not built yet. Stays visible until each one lands. */
@Composable
internal fun ClockToolPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.clock_tool_coming_soon), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DrawerHeader() {
    // No version here: Settings > About is the one place the build number lives.
    Column(Modifier.padding(horizontal = 28.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.drawer_tagline),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DrawerAction(title: String, icon: ImageVector, onClick: () -> Unit, actionNeeded: Boolean = false) {
    NavigationDrawerItem(
        label = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title)
                if (actionNeeded) ActionNeededBadge()
            }
        },
        selected = false,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}
