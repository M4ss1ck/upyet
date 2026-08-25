package dev.upyet.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.upyet.R
import dev.upyet.alarm.ui.AlarmListScreen
import dev.upyet.history.ui.HistoryScreen
import dev.upyet.settings.ui.SettingsScreen
import kotlinx.coroutines.launch

internal data class Tab(val labelRes: Int, val icon: ImageVector)

internal val tabs =
    listOf(
        Tab(R.string.alarms, Icons.Default.Alarm),
        Tab(R.string.history, Icons.Default.History),
        Tab(R.string.settings, Icons.Default.Settings),
    )

@Composable
fun TabsScreen(onEdit: (Long) -> Unit, onOpenOccurrence: (Long) -> Unit, onReliability: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    TabsScaffold(pagerState) { page ->
        when (page) {
            0 -> AlarmListScreen(onEdit = onEdit, onReliability = onReliability)
            1 -> HistoryScreen(onOpen = onOpenOccurrence)
            else -> SettingsScreen(onReliability = onReliability)
        }
    }
}

@Composable
internal fun TabsScaffold(pagerState: PagerState, page: @Composable (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    // Android's expected back behaviour for bottom-bar tabs: return to the first tab, then leave the app.
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        icon = { Icon(tab.icon, stringResource(tab.labelRes)) },
                        label = { Text(stringResource(tab.labelRes)) },
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
        HorizontalPager(pagerState, Modifier.padding(padding)) { page(it) }
    }
}
