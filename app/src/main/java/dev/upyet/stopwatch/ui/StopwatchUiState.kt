package dev.upyet.stopwatch.ui

import androidx.compose.runtime.Immutable
import dev.upyet.stopwatch.domain.LapRow
import dev.upyet.stopwatch.domain.Stopwatch

/** What the Stopwatch tab shows: the stored stopwatch, and the laps derived from it (oldest first). */
@Immutable
data class StopwatchUiState(val stopwatch: Stopwatch = Stopwatch.IDLE) {
    val laps: List<LapRow> get() = stopwatch.lapRows()
}

/** Every event the Stopwatch tab can raise. Each one is a single command to the controller; the screen holds no logic. */
@Immutable
data class StopwatchCallbacks(
    val onStart: () -> Unit = {},
    val onPause: () -> Unit = {},
    val onLap: () -> Unit = {},
    val onReset: () -> Unit = {},
)
