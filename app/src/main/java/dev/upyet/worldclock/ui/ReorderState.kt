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
 *
 * Two detectors can start a drag on the same row: the handle's (immediate) and the row's (after a long press). A
 * press held on the handle starts both. Only the [DragSource] that started a drag may move, end or cancel it, and a
 * detector consumes a move only when [drag] says it owns the drag: otherwise the owner would see consumed events,
 * cancel, and throw the drag away on release.
 */
internal class ReorderState(private val listState: LazyListState, private val commit: (List<Long>) -> Unit) {
    var order by mutableStateOf<List<Long>>(emptyList())
        private set
    var draggedId by mutableStateOf<Long?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    private var startOrder: List<Long> = emptyList()
    private var owner: DragSource? = null

    /** Adopts the stored order, except in the middle of a drag, which owns the order until it ends. */
    fun sync(ids: List<Long>) {
        if (draggedId == null && order != ids) order = ids
    }

    fun start(id: Long, source: DragSource) {
        if (draggedId != null) return
        startOrder = order
        draggedId = id
        owner = source
        dragOffset = 0f
    }

    /** Returns whether [source] owns the drag, which is when the caller should consume the move. */
    fun drag(source: DragSource, deltaY: Float): Boolean {
        if (owner != source) return false
        moveBy(deltaY)
        return true
    }

    private fun moveBy(deltaY: Float) {
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

    fun end(source: DragSource) {
        if (owner != source) return
        val finished = order
        release()
        if (finished != startOrder) commit(finished)
    }

    fun cancel(source: DragSource) {
        if (owner != source) return
        order = startOrder
        release()
    }

    private fun release() {
        draggedId = null
        owner = null
        dragOffset = 0f
    }

    private fun crossed(rows: List<LazyListItemInfo>, dragged: LazyListItemInfo, centre: Float): LazyListItemInfo? {
        fun mid(info: LazyListItemInfo) = info.offset + info.size / 2f
        val below = rows.filter { it.offset > dragged.offset }.firstOrNull { centre > mid(it) }
        val above = rows.filter { it.offset < dragged.offset }.lastOrNull { centre < mid(it) }
        return below ?: above
    }
}

/** Which detector started a drag: the handle's immediate one or the row's long-press one. */
internal enum class DragSource { Handle, Row }
