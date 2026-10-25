// PROTOTYPE for #15, throwaway: one world clock screen, the variant the review kept, with a world map above
// the home clock. No ViewModel, no Room, no DataStore, no Hilt - one in-memory list of places over a bundled
// Natural Earth extract (WorldClockData.kt) and a Canvas map of the same list (WorldClockMap.kt). The ticker
// is display only: one recomposition a minute, nothing scheduled.
//
// What it answers now: does a "where are my places" map earn the height it takes above the reading, and does
// the DeskClock list still carry a list of six? B (analog grid) and C (day timeline) were dropped with the
// decision, so this file holds no comparison, only the survivor and its map.
package dev.upyet.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * #15, "How should the world clock screen behave?".
 *
 * The review's answer was variant A, the DeskClock list, plus a map: every listed place marked on a world
 * map above the home clock, and a city search backed by real place data instead of ICU's one-city-per-zone
 * export. So this screen is about two things only - whether the map earns its height, and whether a list of
 * rows under it still reads at a glance.
 */
@Composable
internal fun WorldClockPrototype() {
    val context = LocalContext.current.applicationContext
    // Reading is 5300 rows and one coastline, off the main thread; until it lands the screen says so rather
    // than showing an empty list that looks like a bug.
    var assets by remember { mutableStateOf<WorldClockAssets?>(null) }
    LaunchedEffect(Unit) { assets = WorldClockAssetsLoader.load(context) }
    val loaded = assets
    if (loaded == null) {
        PrototypeLoading(message = "Reading the place list…")
    } else {
        WorldClockLoaded(assets = loaded)
    }
}

@Composable
private fun PrototypeLoading(message: String) {
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WorldClockLoaded(assets: WorldClockAssets) {
    val lab = remember(assets) { WorldClockLab(assets) }
    var now by remember { mutableStateOf(Instant.now()) }
    // Display only. A minute is the resolution a world clock is read at, and the ticker schedules nothing:
    // the real screen reads the system clock the same way.
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val variants = listOf(PrototypeVariant("A", "DeskClock list") { DeskClockListVariant(lab = lab, now = now) })
    PrototypeVariants(variants = variants, modifier = Modifier.statusBarsPadding()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Madrid and Honolulu exist to make the map and the day labels do something: Tokyo is tomorrow
            // from both, and Honolulu is yesterday from everything east of it.
            PrototypeCycleChip(label = "Home", value = lab.homeLabel, onNext = { lab.cycleHome() })
        }
    }
}

// region Shared in-memory model

/** A Home chip choice. A null zone means "whatever the device is set to", which is the real default. */
private class HomeChoice(val label: String, val zoneId: String?)

private val homeChoices =
    listOf(
        HomeChoice("device", null),
        HomeChoice("Madrid", "Europe/Madrid"),
        HomeChoice("Honolulu", "Pacific/Honolulu"),
    )

/**
 * London, New York, Tokyo and Mumbai: one place on the same offset as Europe, one behind it, one ahead of
 * it, and one half an hour off. Picked out of the extract by country as well as name, because London,
 * Ontario is a different city in a different zone.
 */
private fun seedPlaces(cities: List<WorldCity>): List<WorldCity> {
    val wanted = listOf("GB" to "London", "US" to "New York", "JP" to "Tokyo", "IN" to "Mumbai")
    return wanted.mapNotNull { (isoA2, name) -> cities.firstOrNull { it.isoA2 == isoA2 && it.nameAscii == name } }
}

/**
 * One list of places, shared by the screen and its map. Nothing here is persisted and nothing is ordered by
 * time; [selectedKey] is the one piece of state the map and the list both write, which is what keeps "tap a
 * row, light up its marker" and "tap a marker, light up its row" from ever disagreeing.
 */
private class WorldClockLab(assets: WorldClockAssets) {
    val cities: List<WorldCity> = assets.cities
    val land: List<FloatArray> = assets.land

    /** A zone has many cities; the home chip needs one coordinate, so it takes the most populous. */
    private val bestCityByZone: Map<String, WorldCity> =
        cities.groupBy { it.zone.id }.mapValues { (_, zoneCities) ->
            zoneCities.reduce { best, city -> if (city.popMax > best.popMax) city else best }
        }

    val places = mutableStateListOf<WorldCity>().apply { addAll(seedPlaces(cities)) }
    private var homeIndex by mutableIntStateOf(0)
    var selectedKey by mutableStateOf<String?>(null)

    val homeLabel: String get() = homeChoices[homeIndex].label
    val homeZone: ZoneId get() = homeChoices[homeIndex].zoneId?.let(ZoneId::of) ?: ZoneId.systemDefault()

    /** Null when the device's own zone has no city in the extract: then the map simply has no home marker. */
    val homeCity: WorldCity? get() = bestCityByZone[homeZone.id]
    val homeCityName: String get() = homeCity?.displayName() ?: homeZone.id.substringAfterLast('/')

    fun cycleHome() {
        homeIndex = (homeIndex + 1) % homeChoices.size
    }

    fun isAdded(city: WorldCity): Boolean = places.any { it.key == city.key }

    /** Adding the same place twice would be two rows for one time and two identical list keys. */
    fun add(city: WorldCity) {
        if (isAdded(city)) return
        places.add(city)
    }

    fun remove(city: WorldCity) {
        places.remove(city)
        if (selectedKey == city.key) selectedKey = null
    }

    fun moveBy(city: WorldCity, delta: Int) {
        val from = places.indexOf(city)
        val to = from + delta
        if (from < 0 || to !in places.indices) return
        places.add(to, places.removeAt(from))
    }

    fun select(city: WorldCity) {
        selectedKey = if (selectedKey == city.key) null else city.key
    }
}

@Composable
private fun EmptyPlaces(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
    )
}

/** The search field: one field, one wording, wherever it is pinned. */
@Composable
private fun WorldQueryField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(text = "City or country") },
        singleLine = true,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** A small glyph button: the prototypes use text instead of icons so nothing depends on an icon set. */
@Composable
private fun WorldGlyphButton(glyph: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One search hit: the place, its current time and how far that time is from home. Population order is the
 * whole ranking - "san" should reach San Antonio before a village - so the list reads as a list of real
 * places from the first keystroke.
 */
@Composable
private fun CitySearchRow(city: WorldCity, homeZone: ZoneId, now: Instant, added: Boolean, onPick: () -> Unit) {
    val moment = city.moment(now, homeZone)
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !added, onClick = onPick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = city.title(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = moment.detailLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            text = formatClock(moment.time),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.End,
        )
        if (added) PrototypeTag(text = "Added", color = MaterialTheme.colorScheme.primary)
    }
}

/** The result rows of the full-screen picker. */
@Composable
private fun WorldSearchResults(
    hits: List<WorldCity>,
    lab: WorldClockLab,
    now: Instant,
    onPicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (hits.isEmpty()) {
        EmptyPlaces(message = "No place matches that. A country name works too: try \"spain\".")
        return
    }
    val homeZone = lab.homeZone
    LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(hits, key = { it.key }) { city ->
            CitySearchRow(
                city = city,
                homeZone = homeZone,
                now = now,
                added = lab.isAdded(city),
                onPick = {
                    lab.add(city)
                    onPicked()
                },
            )
        }
    }
}

// endregion

// region The one variant - DeskClock list under a world map, swipe to remove, full-screen picker

@Composable
private fun DeskClockListVariant(lab: WorldClockLab, now: Instant) {
    var picking by rememberSaveable { mutableStateOf(false) }
    // Hoisted so a marker tap can scroll its row into view: a highlight the reviewer cannot see is not an
    // answer to "does tapping the map work".
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val home = ZonedDateTime.ofInstant(now, lab.homeZone)
    if (picking) {
        FullScreenPicker(lab = lab, now = now, onClose = { picking = false })
        return
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PrototypeBareTitle(title = "World clock", subtitle = "A · DeskClock list under a world map, swipe a row away to remove it")
            WorldMapCard(
                places = lab.places,
                home = lab.homeCity,
                land = lab.land,
                selectedKey = lab.selectedKey,
                now = now,
                onMarkerTap = { city ->
                    lab.select(city)
                    val index = lab.places.indexOfFirst { it.key == city.key }
                    if (index >= 0 && lab.selectedKey == city.key) scope.launch { listState.animateScrollToItem(index) }
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = formatClock(home), style = MaterialTheme.typography.displayLarge)
                Text(
                    text = home.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Home · ${lab.homeCityName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(Modifier.padding(top = 10.dp))
            if (lab.places.isEmpty()) {
                EmptyPlaces(message = "No places. Tap + to search the bundled place list.")
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    // Clears the FAB this layout pins, not the prototype pill.
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(lab.places, key = { it.key }) { place ->
                        val index = lab.places.indexOf(place)
                        SwipeToRemoveRow(
                            city = place,
                            moment = place.moment(now, lab.homeZone),
                            selected = lab.selectedKey == place.key,
                            canMoveUp = index > 0,
                            canMoveDown = index in 0 until lab.places.lastIndex,
                            onSelect = { lab.select(place) },
                            onMove = { delta -> lab.moveBy(place, delta) },
                            onRemove = { lab.remove(place) },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { picking = true },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 16.dp),
        ) {
            Text(text = "+", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

/**
 * The row the DeskClock reading asks for: city, then how far its day and its hour are from home, then the
 * time on the right where a column of times is scanned.
 *
 * A row is also the list half of the map link: tapping it lights its marker, and the tint it gets back is
 * the same selection the map draws. Reordering stays the cheap version on purpose - a handle plus two taps
 * instead of a drag - because what is under review is the reading order, not the gesture.
 */
@Composable
private fun SwipeToRemoveRow(
    city: WorldCity,
    moment: CityMoment,
    selected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { onRemove() },
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = "Remove",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Surface(color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSelect)
                    .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = city.title(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = moment.detailLine(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(text = formatClock(moment.time), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.End)
                Column {
                    WorldGlyphButton(glyph = "↑", enabled = canMoveUp) { onMove(-1) }
                    WorldGlyphButton(glyph = "↓", enabled = canMoveDown) { onMove(+1) }
                }
            }
        }
    }
}

@Composable
private fun FullScreenPicker(lab: WorldClockLab, now: Instant, onClose: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val hits = remember(query, lab.cities) { searchCities(lab.cities, query) }
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Text(text = "←", style = MaterialTheme.typography.titleLarge) }
            Text(text = "Add a place", style = MaterialTheme.typography.titleLarge)
        }
        WorldQueryField(query = query, onQueryChange = { query = it })
        Text(
            text = "Natural Earth places of 15 000 people and up, searched in English and in Spanish.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        if (query.isBlank()) {
            Box(Modifier.weight(1f)) {
                EmptyPlaces(
                    message = "Type a city or country. \"tokio\" and \"spain\" both work: Spanish names and the " +
                        "localized country names are searched as well as the English ones.",
                )
            }
        } else {
            Text(
                text = "${hits.size} places",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
            WorldSearchResults(hits = hits, lab = lab, now = now, onPicked = onClose, modifier = Modifier.weight(1f))
        }
    }
}

// endregion
