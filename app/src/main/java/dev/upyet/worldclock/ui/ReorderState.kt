package dev.upyet.worldclock.ui

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Drag-to-reorder for a `LazyColumn` whose place rows are keyed by their `Long` id.
 *
 * While a drag runs, [order] is the local truth: the dragged row follows the finger through [dragOffset] and swaps
 * with a neighbour once its centre crosses that neighbour's centre. The ViewModel hears about it once, from [end],
 * so a drag that wanders and comes back writes nothing.
 */
internal class ReorderState(private val listState: LazyListState, private val commit: (List<Long>) -> Unit) {
    var order by mutableStateOf<List<Long>>(emptyList())
        private set
    var draggedId by mutableStateOf<Long?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    private var startOrder: List<Long> = emptyList()

    /** Adopts the stored order, except in the middle of a drag, which owns the order until it ends. */
    fun sync(ids: List<Long>) {
        if (draggedId == null && order != ids) order = ids
    }

    fun start(id: Long) {
        if (draggedId != null) return
        startOrder = order
        draggedId = id
        dragOffset = 0f
    }

    fun drag(deltaY: Float) {
        val id = draggedId ?: return
        dragOffset += deltaY
        val rows = listState.layoutInfo.visibleItemsInfo.filter { it.key is Long }
        val dragged = rows.firstOrNull { it.key == id } ?: return
        // A swap changes the order before the list has measured it. Until the layout catches up its offsets
        // describe the old order, and acting on them would swap twice for one crossing.
        val keys = rows.map { it.key as Long }
        val first = keys.firstOrNull()?.let(order::indexOf) ?: return
        if (first < 0 || order.subList(first, minOf(first + keys.size, order.size)) != keys) return
        val centre = dragged.offset + dragOffset + dragged.size / 2f
        val target = crossed(rows, dragged, centre) ?: return
        val from = order.indexOf(id)
        val targetId = target.key as Long
        val to = order.indexOf(targetId)
        if (from < 0 || to < 0) return
        order = order.toMutableList().also {
            it[from] = targetId
            it[to] = id
        }
        // The dragged row now sits where the neighbour was, so the finger offset is measured from there.
        dragOffset += if (to > from) -target.size else target.size
    }

    fun end() {
        val finished = order
        draggedId = null
        dragOffset = 0f
        if (finished != startOrder) commit(finished)
    }

    fun cancel() {
        order = startOrder
        draggedId = null
        dragOffset = 0f
    }

    private fun crossed(rows: List<LazyListItemInfo>, dragged: LazyListItemInfo, centre: Float): LazyListItemInfo? {
        fun mid(info: LazyListItemInfo) = info.offset + info.size / 2f
        val below = rows.filter { it.offset > dragged.offset }.firstOrNull { centre > mid(it) }
        val above = rows.filter { it.offset < dragged.offset }.lastOrNull { centre < mid(it) }
        return below ?: above
    }
}
