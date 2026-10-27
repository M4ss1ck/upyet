package dev.upyet.worldclock.ui

import androidx.compose.foundation.lazy.LazyListState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The ownership half of the held-handle fix: a press held on a row's handle starts two detectors, and only the one
 * that started the drag may move, end or cancel it. On an emulator, the row's detector cancelling the handle's drag
 * threw every held-handle reorder away on release.
 */
class ReorderStateTest {
    private val commits = mutableListOf<List<Long>>()
    private val state = ReorderState(LazyListState()) { commits += it }.apply { sync(listOf(1L, 2L, 3L)) }

    @Test
    fun onlyTheStartingSourceOwnsTheDrag() {
        state.start(1L, DragSource.Row)
        state.start(1L, DragSource.Handle)

        assertThat(state.drag(DragSource.Handle, 10f)).isFalse()
        assertThat(state.drag(DragSource.Row, 10f)).isTrue()
    }

    @Test
    fun aNonOwnerCancelLeavesTheDragRunning() {
        state.start(2L, DragSource.Row)

        state.cancel(DragSource.Handle)
        state.end(DragSource.Handle)

        assertThat(state.draggedId).isEqualTo(2L)
    }

    @Test
    fun theOwnerEndsTheDragAndADragThatMovedNothingCommitsNothing() {
        state.start(2L, DragSource.Handle)

        state.end(DragSource.Handle)

        assertThat(state.draggedId).isNull()
        assertThat(state.dragOffset).isEqualTo(0f)
        assertThat(commits).isEmpty()
    }

    @Test
    fun afterTheOwnerReleasesAnotherSourceMayStart() {
        state.start(1L, DragSource.Row)
        state.cancel(DragSource.Row)

        state.start(3L, DragSource.Handle)

        assertThat(state.draggedId).isEqualTo(3L)
        assertThat(state.drag(DragSource.Handle, 5f)).isTrue()
    }

    @Test
    fun noSourceOwnsADragThatNeverStarted() {
        assertThat(state.drag(DragSource.Row, 5f)).isFalse()
        assertThat(state.drag(DragSource.Handle, 5f)).isFalse()
    }
}
