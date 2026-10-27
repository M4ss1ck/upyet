package dev.upyet.worldclock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.upyet.R
import dev.upyet.core.ui.components.ClockText
import dev.upyet.core.ui.currentLocale
import dev.upyet.core.ui.rememberLocalized
import dev.upyet.worldclock.domain.WorldClockRow
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The map and the home time ahead of the first place row; the list index of a place is its position plus this. */
private const val HEADER_ITEMS = 2
private val SCREEN_PADDING = 16.dp

/** Room under the last row for the add button, so it never covers a row's time. */
private val FAB_CLEARANCE = 88.dp

/**
 * The scrolling body: map, home time, then the place rows (or the empty state). Rows are keyed by their GeoNames
 * id, so they keep their identity through reorder, removal and undo.
 */
@Composable
internal fun WorldClockList(state: WorldClockUiState.Ready, callbacks: WorldClockCallbacks, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val onReorder by rememberUpdatedState(callbacks.onReorder)
    val reorder = remember(listState) { ReorderState(listState) { onReorder(it) } }
    val ids = state.rows.map { it.row.geonamesId }
    LaunchedEffect(ids) { reorder.sync(ids) }
    // Until the effect has run (the first frame) the stored order is the one to show.
    val shown = reorder.order.takeIf { it.size == ids.size && it.containsAll(ids) } ?: ids
    val rowsById = state.rows.associateBy { it.row.geonamesId }
    val actions = RowActions(
        onSelect = callbacks.onSelect,
        onRemove = callbacks.onRemove,
        onMove = { id, direction -> callbacks.onReorder(moved(ids, id, direction)) },
        reorder = reorder,
    )
    val onPlaceTap = { id: Long ->
        callbacks.onSelect(id)
        val index = shown.indexOf(id)
        if (index >= 0 && id != state.selectedId) scope.launch { listState.animateScrollToItem(HEADER_ITEMS + index) }
        Unit
    }

    LazyColumn(modifier.fillMaxWidth(), state = listState, contentPadding = PaddingValues(bottom = FAB_CLEARANCE)) {
        item(key = "map") { MapCard(state, onPlaceTap) }
        item(key = "home") { HomeTime(state) }
        if (state.rows.isEmpty()) {
            item(key = "empty") { EmptyPlaces() }
        } else {
            itemsIndexed(shown, key = { _, id -> id }) { index, id ->
                val item = rowsById[id] ?: return@itemsIndexed
                val dragging = reorder.draggedId == id
                SwipeableRow(
                    item = item,
                    selected = state.selectedId == id,
                    position = RowPosition(canMoveUp = index > 0, canMoveDown = index < shown.lastIndex),
                    actions = actions,
                    modifier = if (dragging) {
                        Modifier.zIndex(1f).graphicsLayer { translationY = reorder.dragOffset }
                    } else {
                        Modifier.animateItem()
                    },
                )
            }
        }
    }
}

/** [ids] with [id] swapped one place in [direction] (-1 up, +1 down). Out of range leaves the order alone. */
private fun moved(ids: List<Long>, id: Long, direction: Int): List<Long> {
    val from = ids.indexOf(id)
    val to = from + direction
    if (from < 0 || to !in ids.indices) return ids
    return ids.toMutableList().also {
        it[from] = ids[to]
        it[to] = id
    }
}

@Composable
private fun MapCard(state: WorldClockUiState.Ready, onPlaceTap: (Long) -> Unit) {
    val locale = currentLocale()
    val places = remember(state.rows, locale) {
        state.rows.mapNotNull { (it.row as? WorldClockRow.Place)?.city }.map {
            MapPlace(it.geonamesId, cityName(it, locale), it.latitude, it.longitude)
        }
    }
    val home = remember(state.homeCity, places, locale) {
        state.homeCity?.let { city ->
            // A home place that is not listed is a marker with nothing to select.
            MapPlace(city.geonamesId.takeIf { id -> places.any { it.id == id } }, cityName(city, locale), city.latitude, city.longitude)
        }
    }
    WorldMap(
        land = state.land,
        now = state.now,
        places = places,
        home = home,
        selectedId = state.selectedId,
        onPlaceTap = onPlaceTap,
        modifier = Modifier.padding(horizontal = SCREEN_PADDING, vertical = 8.dp),
    )
}

@Composable
private fun HomeTime(state: WorldClockUiState.Ready) {
    val dateFormatter = rememberLocalized { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(it) }
    val zoned = state.now.atZone(state.homeZone)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = SCREEN_PADDING, vertical = 8.dp).testTag("world_clock_home"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ClockText(zoned.toLocalTime(), style = MaterialTheme.typography.displayLarge)
        Text(dateFormatter.format(zoned), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        state.homeLabel?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyPlaces() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.world_clock_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.world_clock_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
