package dev.upyet.stopwatch.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.upyet.R
import dev.upyet.stopwatch.domain.LapRow
import dev.upyet.stopwatch.domain.Stopwatch
import java.time.Duration

/**
 * Issue #13's variant B, enlarged: the dial takes 88 percent of the width. On a short or landscape window that
 * would push the digits, the buttons and the laps off screen, so the dial is also capped at 45 percent of the
 * height; on a portrait phone the width always wins.
 */
private const val DIAL_WIDTH_FRACTION = 0.88f
private const val DIAL_HEIGHT_FRACTION = 0.45f
private val SCREEN_PADDING = 16.dp
private val BUTTON_SIZE = 80.dp
private val PRIMARY_BUTTON_SIZE = 96.dp
private val PRIMARY_ICON_SIZE = 40.dp
private val SECONDARY_ICON_SIZE = 28.dp
private val LAP_ROW_PADDING = 12.dp

/** From one hour the time gains an "H:" field, and at the large size "10:00:00.00" no longer fits a phone's width. */
private val ONE_HOUR: Duration = Duration.ofHours(1)

/** The Stopwatch tab. Owns the frame clock for the display; every command goes to [StopwatchViewModel]. */
@Composable
fun StopwatchScreen(viewModel: StopwatchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val callbacks = remember(viewModel) {
        StopwatchCallbacks(
            onStart = viewModel::onStart,
            onPause = viewModel::onPause,
            onLap = viewModel::onLap,
            onReset = viewModel::onReset,
        )
    }
    StopwatchContent(state = state, elapsed = rememberElapsed(state.stopwatch), callbacks = callbacks)
}

/**
 * The time to show. A running stopwatch is recomputed every frame from its anchor, so the digits and the hands move
 * without any timer of ours; a stopped one shows its stored total and never starts the frame loop. The loop ends
 * when the composable leaves, or when the stopwatch changes (`produceState` is keyed on it).
 */
@Composable
internal fun rememberElapsed(stopwatch: Stopwatch): Duration {
    val elapsed by produceState(stopwatch.elapsedAt(elapsedRealtime()), stopwatch) {
        // Set first, so a reset or pause shows the stored value at once instead of the last frame's.
        value = stopwatch.elapsedAt(elapsedRealtime())
        if (!stopwatch.isRunning) return@produceState
        while (true) {
            withFrameMillis { value = stopwatch.elapsedAt(elapsedRealtime()) }
        }
    }
    return elapsed
}

private fun elapsedRealtime(): Duration = Duration.ofMillis(SystemClock.elapsedRealtime())

/** Stateless: everything it shows arrives as parameters, so the screen test can drive it without Hilt. */
@Composable
internal fun StopwatchContent(state: StopwatchUiState, elapsed: Duration, callbacks: StopwatchCallbacks, modifier: Modifier = Modifier) {
    val stopwatch = state.stopwatch
    BoxWithConstraints(modifier.fillMaxSize()) {
        val dial = minOf(maxWidth * DIAL_WIDTH_FRACTION, maxHeight * DIAL_HEIGHT_FRACTION)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = SCREEN_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StopwatchDial(elapsed = elapsed, modifier = Modifier.size(dial).testTag("stopwatch_dial"))
            Text(
                text = formatStopwatch(elapsed),
                style = (if (elapsed < ONE_HOUR) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium)
                    .copy(fontFeatureSettings = "tnum"),
                maxLines = 1,
                modifier = Modifier.padding(vertical = 8.dp).testTag("stopwatch_time"),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(
                    icon = Icons.Default.Flag,
                    description = stringResource(R.string.stopwatch_lap),
                    enabled = stopwatch.isRunning,
                    size = BUTTON_SIZE,
                    testTag = "stopwatch_lap",
                    onClick = callbacks.onLap,
                )
                when {
                    stopwatch.isRunning -> RoundButton(
                        icon = Icons.Default.Pause,
                        description = stringResource(R.string.stopwatch_pause),
                        enabled = true,
                        size = PRIMARY_BUTTON_SIZE,
                        testTag = "stopwatch_start_pause",
                        primary = true,
                        onClick = callbacks.onPause,
                    )

                    else -> RoundButton(
                        icon = Icons.Default.PlayArrow,
                        description = stringResource(if (stopwatch.isIdle) R.string.stopwatch_start else R.string.stopwatch_resume),
                        enabled = true,
                        size = PRIMARY_BUTTON_SIZE,
                        testTag = "stopwatch_start_pause",
                        primary = true,
                        onClick = callbacks.onStart,
                    )
                }
                RoundButton(
                    icon = Icons.Default.Replay,
                    description = stringResource(R.string.stopwatch_reset),
                    enabled = !stopwatch.isIdle,
                    size = BUTTON_SIZE,
                    testTag = "stopwatch_reset",
                    onClick = callbacks.onReset,
                )
            }
            if (state.laps.isEmpty()) {
                Spacer(Modifier.weight(1f))
            } else {
                LapHeader()
                LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("stopwatch_laps")) {
                    items(state.laps, key = { it.number }) { lap -> LapItem(lap) }
                }
            }
        }
    }
}

/** One round control. The primary one is filled with the primary colour; the others are tonal. */
@Composable
private fun RoundButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    size: Dp,
    testTag: String,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    val modifier = Modifier.size(size).testTag(testTag)
    if (primary) {
        FilledIconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(PRIMARY_ICON_SIZE))
        }
    } else {
        // The theme's secondary container is close to the background, so it would leave the circle invisible.
        val colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = modifier, colors = colors) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(SECONDARY_ICON_SIZE))
        }
    }
}

@Composable
private fun LapHeader() {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        val style = MaterialTheme.typography.labelLarge
        Text(stringResource(R.string.stopwatch_lap), Modifier.weight(1f), style = style)
        Text(stringResource(R.string.stopwatch_lap_split), Modifier.weight(1f), style = style, textAlign = TextAlign.End)
        Text(stringResource(R.string.stopwatch_lap_total), Modifier.weight(1f), style = style, textAlign = TextAlign.End)
    }
}

@Composable
private fun LapItem(lap: LapRow) {
    val style: TextStyle = (if (lap.total < ONE_HOUR) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
        .copy(fontFeatureSettings = "tnum")
    Column(Modifier.fillMaxWidth().testTag("stopwatch_lap_row_${lap.number}")) {
        Row(Modifier.fillMaxWidth().padding(vertical = LAP_ROW_PADDING)) {
            Text(stringResource(R.string.stopwatch_lap_number, lap.number), Modifier.weight(1f), style = style)
            Text(formatStopwatch(lap.split), Modifier.weight(1f), style = style, textAlign = TextAlign.End)
            Text(formatStopwatch(lap.total), Modifier.weight(1f), style = style, textAlign = TextAlign.End)
        }
        HorizontalDivider()
    }
}
