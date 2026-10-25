// PROTOTYPE for #12, throwaway: three shell variants switchable from the floating pill.
package dev.upyet.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.upyet.BuildConfig
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * #12, "What should the navigation shell look like?".
 *
 * All three variants answer the same product question - four clock tools, one drawer, one back story -
 * and differ only in where the app's identity and its reliability state live: in a per-tab top bar
 * (A), in a single shared app bar plus a banner (B), or in the content itself with no app bar at all
 * (C). The drawer, the tab bar, the tab bodies and the back rules are shared on purpose, so the
 * comparison stays about structure.
 */
@Composable
internal fun NavigationShellPrototype() {
    // The one flag this prototype exposes: does the app have a reliability problem right now?
    var reliabilityProblem by rememberSaveable { mutableStateOf(true) }
    val variants =
        listOf(
            PrototypeVariant("A", "Classic top bar") { ShellVariantA(reliabilityProblem) },
            PrototypeVariant("B", "Pager + banner") { ShellVariantB(reliabilityProblem) },
            PrototypeVariant("C", "Headline, no app bar") { ShellVariantC(reliabilityProblem) },
        )
    PrototypeVariants(variants = variants) {
        PrototypeControlChip(
            label = "Reliability warning",
            checked = reliabilityProblem,
            onCheckedChange = { reliabilityProblem = it },
        )
    }
}

private val versionLine = "v${BuildConfig.VERSION_NAME} (debug)"
private val stubVersionLine = "Version v${BuildConfig.VERSION_NAME}"

/** The four Clock tools, in the order the bottom bar shows them. Alarms is the launch tab. */
private enum class ShellTab(val title: String, val icon: ImageVector) {
    Alarms("Alarms", Icons.Default.Alarm),
    WorldClock("World clock", Icons.Default.Public),
    Timer("Timer", Icons.Default.HourglassEmpty),
    Stopwatch("Stopwatch", Icons.Default.Timer),
}

/** Drawer destinations: not tabs. Each one is a stub screen in this prototype. */
private val drawerDestinations =
    listOf(
        "History" to Icons.Default.History,
        "Settings" to Icons.Default.Settings,
        "Reliability" to Icons.Default.Warning,
    )

// region Variant A - a top app bar per tab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellVariantA(reliabilityProblem: Boolean) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var openStub by rememberSaveable { mutableStateOf<String?>(null) }
    val tab = ShellTab.entries[tabIndex]

    // Back contracts: drawer closes, then Alarms, then out of the prototype. The prototype only
    // claims the event when it has somewhere of its own to go, so the catalogue gets the last one.
    BackHandler(enabled = drawerState.isOpen || openStub != null || tabIndex != 0) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            openStub != null -> openStub = null
            else -> tabIndex = 0
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader(tagline = true, version = null)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                drawerDestinations.forEach { (title, icon) ->
                    DrawerAction(
                        title = title,
                        icon = icon,
                        badge = null,
                        onClick = {
                            // Close first: the stub replaces the shell underneath the scrim.
                            scope.launch { drawerState.close() }
                            openStub = title
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = versionLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(28.dp),
                )
            }
        },
    ) {
        val stub = openStub
        if (stub == null) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(text = tab.title) },
                        navigationIcon = { DrawerMenuButton(badge = false) { scope.launch { drawerState.open() } } },
                        actions = {
                            if (reliabilityProblem) {
                                IconButton(onClick = { openStub = "Reliability" }) {
                                    BadgedBox(badge = { Badge() }) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Reliability problem",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        },
                    )
                },
                bottomBar = { ShellNavigationBar(selectedIndex = tabIndex, onSelect = { tabIndex = it }) },
                floatingActionButton = { ShellFab(tab = tab) },
            ) { padding ->
                ShellTabBody(tab = tab, modifier = Modifier.padding(padding))
            }
        } else {
            StubScreen(title = stub, versionLine = null, onBack = { openStub = null })
        }
    }
}

// endregion

// region Variant B - one shared title, a pager, a banner on Alarms

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellVariantB(reliabilityProblem: Boolean) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { ShellTab.entries.size })
    var openStub by rememberSaveable { mutableStateOf<String?>(null) }
    val page = pagerState.currentPage

    BackHandler(enabled = drawerState.isOpen || openStub != null || page != 0) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            openStub != null -> openStub = null
            else -> scope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader(tagline = false, version = versionLine)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                drawerDestinations.forEach { (title, icon) ->
                    DrawerAction(
                        title = title,
                        icon = icon,
                        badge = null,
                        onClick = {
                            scope.launch { drawerState.close() }
                            openStub = title
                        },
                    )
                }
            }
        },
    ) {
        val stub = openStub
        if (stub == null) {
            Scaffold(
                topBar = {
                    Column {
                        CenterAlignedTopAppBar(
                            title = { Text(text = "UpYet") },
                            navigationIcon = { DrawerMenuButton(badge = false) { scope.launch { drawerState.open() } } },
                        )
                        // The reliability problem is only a problem for alarms, so it only shows there.
                        if (reliabilityProblem && page == ShellTab.Alarms.ordinal) {
                            ReliabilityBanner(onFix = { openStub = "Reliability" })
                        }
                    }
                },
                bottomBar = {
                    ShellNavigationBar(
                        selectedIndex = page,
                        onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                    )
                },
                floatingActionButton = { ShellFab(tab = ShellTab.entries[page]) },
            ) { padding ->
                // The pager is what makes swiping a first-class way to change tab, and it reads the
                // same state the bottom bar writes, so the two can never disagree.
                HorizontalPager(state = pagerState, modifier = Modifier.padding(padding)) { index ->
                    ShellTabBody(tab = ShellTab.entries[index])
                }
            }
        } else {
            StubScreen(title = stub, versionLine = null, onBack = { openStub = null })
        }
    }
}

// endregion

// region Variant C - no app bar, the content carries the identity

@Composable
private fun ShellVariantC(reliabilityProblem: Boolean) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var openStub by rememberSaveable { mutableStateOf<String?>(null) }
    val tab = ShellTab.entries[tabIndex]

    BackHandler(enabled = drawerState.isOpen || openStub != null || tabIndex != 0) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            openStub != null -> openStub = null
            else -> tabIndex = 0
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader(tagline = true, version = null)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                drawerDestinations.forEach { (title, icon) ->
                    // With no top bar to hang a warning icon on, the drawer is the only place the
                    // problem can be named before the user goes looking for it.
                    val badge = if (title == "Reliability" && reliabilityProblem) "Action needed" else null
                    DrawerAction(
                        title = title,
                        icon = icon,
                        badge = badge,
                        onClick = {
                            scope.launch { drawerState.close() }
                            openStub = title
                        },
                    )
                }
            }
        },
    ) {
        val stub = openStub
        if (stub == null) {
            Scaffold(
                bottomBar = { ShellNavigationBar(selectedIndex = tabIndex, onSelect = { tabIndex = it }) },
                floatingActionButton = { ShellFab(tab = tab) },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    HeadlineBar(tab = tab, badge = reliabilityProblem) { scope.launch { drawerState.open() } }
                    ShellTabBody(tab = tab, modifier = Modifier.weight(1f))
                }
            }
        } else {
            // Variant C keeps the version out of the drawer entirely, so Settings is where it lives.
            StubScreen(
                title = stub,
                versionLine = if (stub == "Settings") stubVersionLine else null,
                onBack = { openStub = null },
            )
        }
    }
}

/** The tab name at display size, with the only navigation affordance beside it. */
@Composable
private fun HeadlineBar(tab: ShellTab, badge: Boolean, onMenu: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) {
            BadgedBox(badge = { if (badge) Badge(Modifier.size(8.dp)) }) {
                Icon(imageVector = Icons.Default.Menu, contentDescription = "Open menu")
            }
        }
        Text(text = tab.title, style = MaterialTheme.typography.displaySmall)
    }
}

// endregion

// region Shared shell pieces

@Composable
private fun DrawerHeader(tagline: Boolean, version: String?) {
    Column(Modifier.padding(horizontal = 28.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = "UpYet", style = MaterialTheme.typography.headlineMedium)
        if (version != null) {
            Text(text = version, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (tagline) {
            Text(
                text = "The alarm clock that proves you woke up",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DrawerAction(title: String, icon: ImageVector, badge: String?, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = title)
                if (badge != null) {
                    Badge { Text(text = badge) }
                }
            }
        },
        selected = false,
        onClick = onClick,
        icon = { Icon(imageVector = icon, contentDescription = null) },
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun DrawerMenuButton(badge: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (badge) Badge(Modifier.size(8.dp)) }) {
            Icon(imageVector = Icons.Default.Menu, contentDescription = "Open menu")
        }
    }
}

@Composable
private fun ShellNavigationBar(selectedIndex: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        ShellTab.entries.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                label = { Text(text = tab.title) },
            )
        }
    }
}

/** Only the two tabs that create something get a "+"; the other two start from zero instead. */
@Composable
private fun ShellFab(tab: ShellTab) {
    if (tab == ShellTab.Alarms || tab == ShellTab.WorldClock) {
        FloatingActionButton(onClick = {}) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
        }
    }
}

@Composable
private fun ReliabilityBanner(onFix: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = "Alarms may not ring reliably",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onFix) { Text(text = "Fix") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StubScreen(title: String, versionLine: String?, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "$title stub", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Stub. Only the shell is under review - this screen exists so the drawer has somewhere to go.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (versionLine != null) {
                Text(
                    text = versionLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// endregion

// region Fake tab bodies

@Composable
private fun ShellTabBody(tab: ShellTab, modifier: Modifier = Modifier) {
    when (tab) {
        ShellTab.Alarms -> AlarmsTab(modifier)
        ShellTab.WorldClock -> WorldClockTab(modifier)
        ShellTab.Timer -> TimerTab(modifier)
        ShellTab.Stopwatch -> StopwatchTab(modifier)
    }
}

private class FakeAlarm(val time: String, val days: String, val label: String)

private val fakeAlarms =
    listOf(
        FakeAlarm("06:40", "Mon–Fri", "Wake up"),
        FakeAlarm("07:15", "Mon–Fri", "Gym"),
        FakeAlarm("09:00", "Sat, Sun", "Late start"),
        FakeAlarm("22:30", "Every day", "Wind down"),
    )

private val timerKeypadRows =
    listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "00"))

/** Room for the last row to breathe; the shell's own bottom bar already clears the system inset. */
private val tabListPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)

@Composable
private fun AlarmsTab(modifier: Modifier = Modifier) {
    val enabled = remember { mutableStateListOf(true, true, false, true) }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = tabListPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(fakeAlarms) { index, alarm ->
            AlarmRow(alarm = alarm, enabled = enabled[index], onToggle = { enabled[index] = it })
        }
    }
}

@Composable
private fun AlarmRow(alarm: FakeAlarm, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = alarm.time,
                        style = MaterialTheme.typography.displaySmall,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = alarm.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(text = alarm.days, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

private class FakeCity(val city: String, val offset: String, val time: String)

private val fakeCities =
    listOf(
        FakeCity("London", "Same as home", "06:42"),
        FakeCity("New York", "5h behind", "01:42"),
        FakeCity("Tokyo", "9h ahead", "15:42"),
    )

@Composable
private fun WorldClockTab(modifier: Modifier = Modifier) {
    // Sampled once: a prototype's fake clock should not redraw the whole tab every second.
    val now = remember {
        LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Home", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = now, style = MaterialTheme.typography.displayLarge)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        fakeCities.forEach { city -> CityRow(city) }
    }
}

@Composable
private fun CityRow(city: FakeCity) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text = city.city, style = MaterialTheme.typography.titleMedium)
            Text(
                text = city.offset,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = city.time, style = MaterialTheme.typography.displaySmall)
    }
}

@Composable
private fun TimerTab(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "00:00:00", style = MaterialTheme.typography.displayLarge)
        Text(
            text = "hours · minutes · seconds",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        TimerKeypad()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TimerKeypad() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        timerKeypadRows.forEach { keys ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.forEach { key ->
                    OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                        Text(text = key, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun StopwatchTab(modifier: Modifier = Modifier) {
    var running by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Text(text = "00:00.00", style = MaterialTheme.typography.displayLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { running = !running }) { Text(text = if (running) "Stop" else "Start") }
            OutlinedButton(onClick = {}) { Text(text = "Lap") }
        }
        Spacer(Modifier.weight(1f))
    }
}

// endregion
