// PROTOTYPE for #13, throwaway: the few helpers the timer and stopwatch prototypes share.
package dev.upyet.prototype

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * How much room a #13 screen leaves free at the bottom. The prototype pill sits 92 dp above the
 * navigation bar and is up to ~70 dp tall, so anything pinned to the bottom - a FAB, a button bar, the
 * last list row - stops this far short of the edge instead of disappearing under it.
 */
internal val PrototypePillClearance: Dp = 168.dp

/**
 * "7:42" below an hour, "1:07:42" above it. A countdown is read at a glance, so the hours field only
 * appears when it carries information.
 */
internal fun formatCountdown(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) "$hours:${twoDigits(minutes)}:${twoDigits(seconds)}" else "$minutes:${twoDigits(seconds)}"
}

/** "05.31", "1:05.31", "1:02:05.31": a stopwatch always shows centiseconds, hours only when they exist. */
internal fun formatStopwatch(millis: Long): String {
    val totalCentis = millis.coerceAtLeast(0) / 10
    val hours = totalCentis / 360_000
    val minutes = ((totalCentis / 6_000) % 60).toInt()
    val seconds = ((totalCentis / 100) % 60).toInt()
    val centis = (totalCentis % 100).toInt()
    return if (hours > 0) {
        "$hours:${twoDigits(minutes)}:${twoDigits(seconds)}.${twoDigits(centis)}"
    } else {
        "$minutes:${twoDigits(seconds)}.${twoDigits(centis)}"
    }
}

/** "+0.42" / "-1.05": a lap compared with the one before it, signed, never colour alone. */
internal fun formatLapDelta(deltaMillis: Long): String {
    val absolute = abs(deltaMillis)
    val seconds = absolute / 1000
    val centis = ((absolute % 1000) / 10).toInt()
    return (if (deltaMillis < 0) "-" else "+") + seconds + "." + twoDigits(centis)
}

/** The only chrome a #13 variant gets: the tab name it stands in for, and which variant it is. */
@Composable
internal fun PrototypeBareTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A tiny state tag ("Ringing", "fastest", "REC"). Text, not colour, so it survives a colour-blind reader. */
@Composable
internal fun PrototypeTag(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')
