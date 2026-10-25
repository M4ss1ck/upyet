// PROTOTYPE for #15, throwaway: three world clock screens, switchable from the floating pill. No ViewModels,
// no Room, no DataStore, no Hilt - one in-memory list of places over the device's own zone data
// (WorldClockData.kt). The ticker is display only: one recomposition a minute, nothing scheduled.
//
// What the three answer: where does "what time is it there" live once there are several places, and how
// much of the list is editable in place? A is the DeskClock reading (big home clock, one row per place,
// swipe to remove); B gives up the reading order for a grid of dials and hides every edit behind one
// toggle; C puts every place on the same 24-hour strip so overlapping waking hours are visible at once.
package dev.upyet.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.lazy.grid.items as gridItems

/**
 * #15, "How should the world clock screen behave?".
 *
 * All three variants share one fake list of places and the real zone data, so the comparison is about
 * structure only: how the home clock reads, how a place is listed, how a place is added, and how much of
 * that editing stays on the screen.
 */
@Composable
internal fun WorldClockPrototype() {
    val lab = remember { WorldClockLab() }
    var now by remember { mutableStateOf(Instant.now()) }
    // Display only. A minute is the resolution a world clock is read at, and the ticker schedules nothing:
    // the real screen reads the system clock the same way.
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val variants =
        listOf(
            PrototypeVariant("A", "DeskClock list") { DeskClockListVariant(lab = lab, now = now) },
            PrototypeVariant("B", "Analog grid") { AnalogGridVariant(lab = lab, now = now) },
            PrototypeVariant("C", "Day timeline") { DayTimelineVariant(lab = lab, now = now) },
        )
    PrototypeVariants(variants = variants) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // The gap in the real data, made switchable: off finds only canonical zones, on finds fifteen
            // more by hand.
            PrototypeControlChip(label = "Alias table", checked = lab.aliasesOn, onCheckedChange = { lab.aliasesOn = it })
            // Madrid and Honolulu exist to make the day labels do something: Tokyo is tomorrow from both,
            // and Honolulu is yesterday from everything east of it.
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

/** Seeded with a spread: same offset as Europe, behind it, ahead of it, and half an hour off. */
private val seedPlaces =
    listOf(
        WorldPlace("Europe/London", "London"),
        WorldPlace("America/New_York", "New York"),
        WorldPlace("Asia/Tokyo", "Tokyo"),
        WorldPlace("Asia/Kolkata", "Kolkata"),
    )

/** One list of places, shared by all three variants. Nothing here is persisted and nothing is ordered by time. */
private class WorldClockLab {
    val catalog = ZoneCatalog()
    val places = mutableStateListOf<WorldPlace>().apply { addAll(seedPlaces) }
    private var homeIndex by mutableIntStateOf(0)
    var aliasesOn by mutableStateOf(false)

    val homeLabel: String get() = homeChoices[homeIndex].label
    val homeZone: ZoneId get() = homeChoices[homeIndex].zoneId?.let(ZoneId::of) ?: ZoneId.systemDefault()
    val homeCityName: String get() = catalog.city(homeZone.id)?.city ?: homeZone.id.substringAfterLast('/')

    fun cycleHome() {
        homeIndex = (homeIndex + 1) % homeChoices.size
    }

    fun isAdded(zoneId: String): Boolean = places.any { it.zoneId == zoneId }

    /** Adding the same zone twice would be two rows for one time and two identical list keys. */
    fun add(hit: WorldSearchHit) {
        if (isAdded(hit.zoneId)) return
        places.add(WorldPlace(zoneId = hit.zoneId, city = hit.city, note = hit.note))
    }

    fun remove(place: WorldPlace) {
        places.remove(place)
    }

    fun moveBy(place: WorldPlace, delta: Int) {
        val from = places.indexOf(place)
        val to = from + delta
        if (from < 0 || to !in places.indices) return
        places.add(to, places.removeAt(from))
    }
}

/** The line under the search field that makes the alias chip's point in words rather than in rows. */
private fun aliasHint(aliasesOn: Boolean): String = if (aliasesOn) {
    "Alias table on: fifteen hand-curated cities resolve to the zone that keeps their time."
} else {
    "Alias table off: San Francisco, Mumbai and thirteen more do not exist here. Only canonical zones do."
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

/** The search field every add path uses: one field, one wording, wherever it is pinned. */
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

/** The result rows shared by the full-screen picker (A) and the inline one (C), given its own sizing. */
@Composable
private fun WorldSearchResults(
    hits: List<WorldSearchHit>,
    lab: WorldClockLab,
    now: Instant,
    onPicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (hits.isEmpty()) {
        EmptyPlaces(message = "No zone matches that. A country name works too: try \"spain\".")
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
        // Alias hits can share a zone with a canonical zone, so the label is part of the key.
        items(hits, key = { "${it.zoneId}|${it.label}" }) { hit ->
            val added = lab.isAdded(hit.zoneId)
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !added) {
                        lab.add(hit)
                        onPicked()
                    }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text = hit.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        // A zone ICU cannot place still has an id worth showing instead of an empty line.
                        text = hit.country.ifEmpty { hit.zoneId },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    text = formatClock(ZonedDateTime.ofInstant(now, ZoneId.of(hit.zoneId))),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.End,
                )
                if (added) PrototypeTag(text = "Added", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// endregion

// region Variant A - DeskClock list, swipe to remove, full-screen picker

@Composable
private fun DeskClockListVariant(lab: WorldClockLab, now: Instant) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val home = ZonedDateTime.ofInstant(now, lab.homeZone)
    if (picking) {
        FullScreenPicker(lab = lab, now = now, onClose = { picking = false })
        return
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PrototypeBareTitle(title = "World clock", subtitle = "A · DeskClock list, swipe a row away to remove it")
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                EmptyPlaces(message = "No places. Tap + to search the device's own zones.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    // Clears the FAB this layout pins, not the prototype pill.
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(lab.places, key = { it.zoneId }) { place ->
                        val index = lab.places.indexOf(place)
                        SwipeToRemoveRow(
                            place = place,
                            moment = place.moment(now, lab.homeZone),
                            canMoveUp = index > 0,
                            canMoveDown = index in 0 until lab.places.lastIndex,
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
 * Reordering is the cheap version on purpose - a handle plus two taps instead of a drag - because what is
 * under review here is the reading order of the list, not the gesture.
 */
@Composable
private fun SwipeToRemoveRow(
    place: WorldPlace,
    moment: CityMoment,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
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
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = place.city,
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
    val hits = remember(query, lab.aliasesOn) { searchZones(lab.catalog, query, lab.aliasesOn) }
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
            text = aliasHint(lab.aliasesOn),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Text(
            text = "${hits.size} zones",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        WorldSearchResults(hits = hits, lab = lab, now = now, onPicked = onClose, modifier = Modifier.weight(1f))
    }
}

// endregion

// region Variant B - analog grid, night tinted, every edit behind one toggle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalogGridVariant(lab: WorldClockLab, now: Instant) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val hits = remember(query, lab.aliasesOn) { searchZones(lab.catalog, query, lab.aliasesOn) }
    val home = ZonedDateTime.ofInstant(now, lab.homeZone)

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = "World clock", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "B · Analog grid, night tinted and tagged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // One toggle for every edit: without it the grid is a reading surface, not an editor.
            TextButton(onClick = { editing = !editing }) { Text(text = if (editing) "Done" else "Edit") }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AnalogClock(time = home, night = false, modifier = Modifier.size(160.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = formatClock(home), style = MaterialTheme.typography.headlineMedium)
                PrototypeTag(text = "Home", color = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = lab.homeCityName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            gridItems(lab.places, key = { it.zoneId }) { place ->
                val index = lab.places.indexOf(place)
                ClockTile(
                    place = place,
                    moment = place.moment(now, lab.homeZone),
                    editing = editing,
                    canMoveBack = index > 0,
                    canMoveForward = index in 0 until lab.places.lastIndex,
                    onRemove = { lab.remove(place) },
                    onMoveBack = { lab.moveBy(place, -1) },
                    onMoveForward = { lab.moveBy(place, +1) },
                )
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(18.dp)).clickable { picking = true },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(text = "+", style = MaterialTheme.typography.headlineMedium)
                        Text(text = "Add a place", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    if (picking) {
        ModalBottomSheet(onDismissRequest = { picking = false }) {
            Text(
                text = "Add a place",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            WorldQueryField(query = query, onQueryChange = { query = it })
            WorldSearchResults(
                hits = hits,
                lab = lab,
                now = now,
                onPicked = { picking = false },
                // A sheet has no bounded height of its own, so the results need one to scroll in.
                modifier = Modifier.heightIn(max = 380.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The night colours are fixed rather than themed: a phone at 03:00 is not showing the light theme anyway. */
private val nightSky = Color(0xFF16213C)
private val nightFace = Color(0xFF1E2B4A)
private val nightInk = Color(0xFFE8EDF8)

/** A dial with no numbers: the hand positions are the reading, and the city name says whose clock it is. */
@Composable
private fun AnalogClock(time: ZonedDateTime, night: Boolean, modifier: Modifier = Modifier) {
    val face = if (night) nightFace else MaterialTheme.colorScheme.surfaceVariant
    val ink = if (night) nightInk else MaterialTheme.colorScheme.onSurface
    val accent = if (night) nightInk else MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        drawCircle(color = face, radius = radius, center = center)
        drawCircle(color = ink.copy(alpha = 0.2f), radius = radius, center = center, style = Stroke(width = 1.dp.toPx()))
        repeat(12) { index ->
            val radians = (index * 30.0 - 90.0) * PI / 180.0
            val outer = radius - radius * 0.08f
            val inner = outer - if (index % 3 == 0) radius * 0.16f else radius * 0.08f
            drawLine(
                color = ink.copy(alpha = if (index % 3 == 0) 0.85f else 0.35f),
                start = center + Offset(cos(radians).toFloat() * inner, sin(radians).toFloat() * inner),
                end = center + Offset(cos(radians).toFloat() * outer, sin(radians).toFloat() * outer),
                strokeWidth = if (index % 3 == 0) 3.dp.toPx() else 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        val hourRadians = (((time.hour % 12) + time.minute / 60f) * 30f - 90f) * PI / 180.0
        drawLine(
            color = ink,
            start = center,
            end = center + Offset(cos(hourRadians).toFloat() * radius * 0.46f, sin(hourRadians).toFloat() * radius * 0.46f),
            strokeWidth = radius * 0.08f,
            cap = StrokeCap.Round,
        )
        val minuteRadians = (time.minute * 6f - 90f) * PI / 180.0
        drawLine(
            color = accent,
            start = center,
            end = center + Offset(cos(minuteRadians).toFloat() * radius * 0.7f, sin(minuteRadians).toFloat() * radius * 0.7f),
            strokeWidth = radius * 0.05f,
            cap = StrokeCap.Round,
        )
        drawCircle(color = accent, radius = radius * 0.06f, center = center)
    }
}

@Composable
private fun ClockTile(
    place: WorldPlace,
    moment: CityMoment,
    editing: Boolean,
    canMoveBack: Boolean,
    canMoveForward: Boolean,
    onRemove: () -> Unit,
    onMoveBack: () -> Unit,
    onMoveForward: () -> Unit,
) {
    val night = moment.isNight
    val detail = if (night) nightInk.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (night) nightSky else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (night) nightInk else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AnalogClock(time = moment.time, night = night, modifier = Modifier.size(76.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = place.city,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Night is a word as well as a tint: the tint alone would be unreadable for some reviewers.
                if (night) PrototypeTag(text = "night", color = nightInk)
            }
            Text(text = formatClock(moment.time), style = MaterialTheme.typography.titleMedium)
            Text(text = moment.detailLine(), style = MaterialTheme.typography.labelSmall, color = detail, maxLines = 1)
            if (editing) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    WorldGlyphButton(glyph = "←", enabled = canMoveBack, onClick = onMoveBack)
                    WorldGlyphButton(glyph = "→", enabled = canMoveForward, onClick = onMoveForward)
                    WorldGlyphButton(glyph = "✕", onClick = onRemove)
                }
            }
        }
    }
}

// endregion

// region Variant C - every place on one 24-hour strip

/** The two columns the strips are inset by, so every strip shares one x axis and the now line lines up. */
private val timelineLabelWidth = 104.dp
private val timelineTimeWidth = 64.dp
private val timelineRowHeight = 52.dp
private const val WAKING_START_HOUR = 8.0
private const val WAKING_END_HOUR = 22.0

@Composable
private fun DayTimelineVariant(lab: WorldClockLab, now: Instant) {
    var query by rememberSaveable { mutableStateOf("") }
    var menuFor by rememberSaveable { mutableStateOf<String?>(null) }
    val hits = remember(query, lab.aliasesOn) { searchZones(lab.catalog, query, lab.aliasesOn) }
    val home = ZonedDateTime.ofInstant(now, lab.homeZone)

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = "World clock", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "C · Day timeline, every strip on the home clock's day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = formatClock(home), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Home · ${lab.homeCityName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Pinned: searching is the only way to add a place here, so it never scrolls away.
        WorldQueryField(query = query, onQueryChange = { query = it })
        if (query.isBlank()) {
            TimelineHourAxis()
            Text(
                text = "Shaded: 08:00–22:00 local. The line is now, on the home clock.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = timelineLabelWidth, bottom = 4.dp),
            )
            if (lab.places.isEmpty()) {
                EmptyPlaces(message = "No places. Search above to add one.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    itemsIndexed(lab.places, key = { _, place -> place.zoneId }) { index, place ->
                        TimelineRow(
                            place = place,
                            moment = place.moment(now, lab.homeZone),
                            homeZone = lab.homeZone,
                            now = now,
                            canMoveUp = index > 0,
                            canMoveDown = index < lab.places.lastIndex,
                            menuOpen = menuFor == place.zoneId,
                            onOpenMenu = { menuFor = place.zoneId },
                            onDismissMenu = { menuFor = null },
                            onMoveUp = {
                                lab.moveBy(place, -1)
                                menuFor = null
                            },
                            onMoveDown = {
                                lab.moveBy(place, +1)
                                menuFor = null
                            },
                            onRemove = {
                                lab.remove(place)
                                menuFor = null
                            },
                        )
                    }
                }
            }
        } else {
            Text(
                text = "${hits.size} zones",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
            WorldSearchResults(
                hits = hits,
                lab = lab,
                now = now,
                onPicked = { query = "" },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Home-day hours, aligned with the strips underneath: 0, 6, 12, 18 inside the strip and 24 at its end. */
@Composable
private fun TimelineHourAxis() {
    Row(Modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.Bottom) {
        Spacer(Modifier.width(timelineLabelWidth))
        Row(Modifier.weight(1f)) {
            listOf(0, 6, 12, 18).forEach { hour ->
                Box(Modifier.weight(1f)) {
                    Text(
                        text = hour.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = "24",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(timelineTimeWidth),
        )
    }
}

@Composable
private fun TimelineRow(
    place: WorldPlace,
    moment: CityMoment,
    homeZone: ZoneId,
    now: Instant,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Box {
        Row(
            modifier = Modifier.fillMaxWidth().height(timelineRowHeight).clickable(onClick = onOpenMenu),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(timelineLabelWidth).padding(start = 20.dp, end = 6.dp)) {
                Text(
                    text = place.city,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = moment.detailLine(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            TimelineStrip(moment = moment, homeZone = homeZone, now = now, modifier = Modifier.weight(1f).fillMaxHeight())
            Text(
                text = formatClock(moment.time),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.End,
                modifier = Modifier.width(timelineTimeWidth).padding(end = 16.dp),
            )
        }
        // Every edit is behind the row itself: one tap on a strip, the three things that can be done to it.
        DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(text = { Text(text = "Move up") }, enabled = canMoveUp, onClick = onMoveUp)
            DropdownMenuItem(text = { Text(text = "Move down") }, enabled = canMoveDown, onClick = onMoveDown)
            DropdownMenuItem(text = { Text(text = "Remove") }, onClick = onRemove)
        }
    }
}

/**
 * One place's day on the home axis: the strip is the home's 00:00–24:00, the shading is this city's
 * 08:00–22:00 mapped onto it, and the line is now. Two strips side by side are directly comparable, so
 * "when are we both awake" is a glance instead of arithmetic.
 */
@Composable
private fun TimelineStrip(moment: CityMoment, homeZone: ZoneId, now: Instant, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val wakingColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val nowColor = MaterialTheme.colorScheme.error
    val home = ZonedDateTime.ofInstant(now, homeZone)
    // Half-hour zones make this fractional; every other zone makes it an integer.
    val deltaHours = (moment.time.offset.totalSeconds - home.offset.totalSeconds) / 3600.0
    val nowFraction = (home.hour + home.minute / 60.0) / 24.0
    Canvas(modifier) {
        val top = 6.dp.toPx()
        val stripHeight = size.height - 12.dp.toPx()
        val corners = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        drawRoundRect(color = trackColor, topLeft = Offset(0f, top), size = Size(size.width, stripHeight), cornerRadius = corners)
        val start = ((WAKING_START_HOUR - deltaHours) % 24.0 + 24.0) % 24.0
        val span = WAKING_END_HOUR - WAKING_START_HOUR
        fun shade(from: Double, to: Double) {
            val left = (from / 24.0 * size.width).toFloat()
            val right = (to / 24.0 * size.width).toFloat()
            drawRoundRect(
                color = wakingColor,
                topLeft = Offset(left, top),
                size = Size(right - left, stripHeight),
                cornerRadius = corners,
            )
        }
        // A waking window that crosses midnight on the home axis has to be drawn as two pieces.
        if (start + span <= 24.0) {
            shade(start, start + span)
        } else {
            shade(start, 24.0)
            shade(0.0, start + span - 24.0)
        }
        val x = (nowFraction * size.width).toFloat()
        drawLine(color = nowColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

// endregion
