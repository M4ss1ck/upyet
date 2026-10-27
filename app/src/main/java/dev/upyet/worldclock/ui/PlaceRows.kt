package dev.upyet.worldclock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.upyet.R
import dev.upyet.core.ui.components.ClockText
import dev.upyet.core.ui.rememberLocalized
import dev.upyet.worldclock.domain.WorldClockRow
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What a row can ask of its list. [onMove] takes -1 (up) or +1 (down). */
internal class RowActions(
    val onSelect: (Long) -> Unit,
    val onRemove: (Long) -> Unit,
    val onMove: (id: Long, direction: Int) -> Unit,
    val reorder: ReorderState,
)

/** Where a row sits in the list, so the accessibility actions offer only the moves that exist. */
internal data class RowPosition(val canMoveUp: Boolean, val canMoveDown: Boolean)

/**
 * One list line inside its swipe-to-remove frame. Swiping start to end reveals "Remove" on the start edge, in the
 * space the row vacates; the time sits at the end, so the affordance never lands under it.
 */
@Composable
internal fun SwipeableRow(
    item: WorldClockRowUi,
    selected: Boolean,
    position: RowPosition,
    actions: RowActions,
    modifier: Modifier = Modifier,
) {
    val id = item.row.geonamesId
    val onRemove by rememberUpdatedState(actions.onRemove)
    val state = rememberSwipeToDismissBoxState()
    // Settling at the end is the confirmation: the row is gone from the list a moment later, and an undo
    // brings it back as a fresh row at rest.
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.StartToEnd) onRemove(id)
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        gesturesEnabled = actions.reorder.draggedId == null,
        backgroundContent = { RemoveBackground(id) },
    ) {
        PlaceRow(item, selected, position, actions)
    }
}

@Composable
private fun RemoveBackground(id: Long) {
    // The label is decoration for sighted swipers: the "Remove" custom action is the accessible route.
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.padding(start = 16.dp).clearAndSetSemantics { testTag = "world_clock_remove_background_$id" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                stringResource(R.string.world_clock_remove),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun PlaceRow(item: WorldClockRowUi, selected: Boolean, position: RowPosition, actions: RowActions) {
    val row = item.row
    val id = row.geonamesId
    val dragging = actions.reorder.draggedId == id
    val remove = stringResource(R.string.world_clock_remove)
    val moveUp = stringResource(R.string.world_clock_move_up)
    val moveDown = stringResource(R.string.world_clock_move_down)
    val description = rowDescription(row)
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                when {
                    dragging -> MaterialTheme.colorScheme.surfaceContainerHighest
                    selected -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surface
                },
            )
            .testTag("world_clock_row_$id")
            .semantics(mergeDescendants = true) {
                // The row announces what it shows; a screen-reader user reorders and removes through these
                // actions, since a swipe or a long-press drag is not a gesture they can be asked to make.
                if (description != null) contentDescription = description
                customActions = buildList {
                    if (position.canMoveUp) {
                        add(
                            CustomAccessibilityAction(moveUp) {
                                actions.onMove(id, -1)
                                true
                            },
                        )
                    }
                    if (position.canMoveDown) {
                        add(
                            CustomAccessibilityAction(moveDown) {
                                actions.onMove(id, 1)
                                true
                            },
                        )
                    }
                    add(
                        CustomAccessibilityAction(remove) {
                            actions.onRemove(id)
                            true
                        },
                    )
                }
            }
            .pointerInput(id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { actions.reorder.start(id) },
                    onDrag = { change, amount ->
                        change.consume()
                        actions.reorder.drag(amount.y)
                    },
                    onDragEnd = actions.reorder::end,
                    onDragCancel = actions.reorder::cancel,
                )
            }
            .clickable { actions.onSelect(id) },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DragHandle(id, actions.reorder)
            when (row) {
                is WorldClockRow.Place -> PlaceDetails(row, item.zoneLabel, Modifier.weight(1f))
                is WorldClockRow.Unavailable -> UnavailableDetails(id, actions.onRemove, Modifier.weight(1f))
            }
            if (row is WorldClockRow.Place) {
                Spacer(Modifier.width(12.dp))
                ClockText(row.localTime.toLocalTime(), Modifier.testTag("world_clock_time_$id"))
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun rowDescription(row: WorldClockRow): String? {
    if (row !is WorldClockRow.Place) return null
    val formatter = rememberLocalized { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(it) }
    return stringResource(R.string.world_clock_row_description, placeTitle(row.city), dayAndOffset(row), formatter.format(row.localTime))
}

@Composable
private fun DragHandle(id: Long, reorder: ReorderState) {
    Icon(
        Icons.Default.DragHandle,
        contentDescription = stringResource(R.string.world_clock_drag_handle),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(48.dp)
            .padding(12.dp)
            .pointerInput(id) {
                detectDragGestures(
                    onDragStart = { reorder.start(id) },
                    onDrag = { change, amount ->
                        change.consume()
                        reorder.drag(amount.y)
                    },
                    onDragEnd = reorder::end,
                    onDragCancel = reorder::cancel,
                )
            },
    )
}

@Composable
private fun PlaceDetails(row: WorldClockRow.Place, zoneLabel: String?, modifier: Modifier) {
    val zone = zoneLabelText(row, zoneLabel)
    Column(modifier.padding(vertical = 12.dp)) {
        Text(placeTitle(row.city), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(dayAndOffset(row), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (zone != null) {
            Text(zone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UnavailableDetails(id: Long, onRemove: (Long) -> Unit, modifier: Modifier) {
    Column(modifier.padding(vertical = 8.dp)) {
        Text(stringResource(R.string.world_clock_place_unavailable), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.world_clock_place_unavailable_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { onRemove(id) }, modifier = Modifier.testTag("world_clock_remove_$id")) {
            Text(stringResource(R.string.world_clock_remove))
        }
    }
}
