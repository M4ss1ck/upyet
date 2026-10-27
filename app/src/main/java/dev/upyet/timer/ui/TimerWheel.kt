package dev.upyet.timer.ui

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.upyet.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.math.abs

private val ItemHeight = 64.dp
private const val VISIBLE_ITEMS = 3
private const val NEIGHBOUR_ALPHA = 0.38f

/**
 * A snapping number wheel: three rows in view, the centred one large, its neighbours small and dimmed.
 *
 * The list pads its content by one row at each end, so the centred row's index in the list is exactly
 * `value - range.first` and the fling snaps rows into the centre. [value] stays the single source of
 * truth: a settled scroll reports the centred value, and a change from outside (a preset chip) scrolls
 * the wheel to it, so the wheels and the presets can never disagree.
 */
@Composable
fun TimerWheel(value: Int, range: IntRange, label: String, tag: String, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val count = range.last - range.first + 1
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (value - range.first).coerceIn(0, count - 1))
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val centred by remember(listState) { derivedStateOf { listState.centredIndex() } }

    LaunchedEffect(value, range) {
        val target = (value - range.first).coerceIn(0, count - 1)
        if (listState.centredIndex() != target && !listState.isScrollInProgress) listState.animateScrollToItem(target)
    }
    LaunchedEffect(listState, range) {
        snapshotFlow { listState.isScrollInProgress to listState.centredIndex() }
            .filter { (scrolling, _) -> !scrolling }
            .map { (_, index) -> range.first + index }
            .distinctUntilChanged()
            .collect { settled -> if (settled != currentValue) currentOnChange(settled) }
    }

    val description = stringResource(R.string.timer_wheel_description, label, value)
    val increase = stringResource(R.string.timer_wheel_increase)
    val decrease = stringResource(R.string.timer_wheel_decrease)
    Box(modifier.height(ItemHeight * VISIBLE_ITEMS), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(ItemHeight),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ) {}
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(vertical = ItemHeight),
            modifier = Modifier
                .testTag(tag)
                .semantics {
                    contentDescription = description
                    customActions = listOf(
                        CustomAccessibilityAction(increase) {
                            val ok = currentValue < range.last
                            if (ok) currentOnChange(currentValue + 1)
                            ok
                        },
                        CustomAccessibilityAction(decrease) {
                            val ok = currentValue > range.first
                            if (ok) currentOnChange(currentValue - 1)
                            ok
                        },
                    )
                },
        ) {
            itemsIndexed(List(count) { range.first + it }) { index, number ->
                val selected = index == centred
                Box(Modifier.fillMaxWidth().height(ItemHeight), contentAlignment = Alignment.Center) {
                    Text(
                        text = number.toString().padStart(2, '0'),
                        style = if (selected) MaterialTheme.typography.displayMedium else MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.alpha(if (selected) 1f else NEIGHBOUR_ALPHA).clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}

/** The row nearest the viewport's centre, which is the wheel's value because of the one-row padding. */
private fun LazyListState.centredIndex(): Int {
    val info = layoutInfo
    val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - centre) }?.index ?: firstVisibleItemIndex
}
