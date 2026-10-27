package dev.upyet.stopwatch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import java.time.Duration
import kotlin.math.cos
import kotlin.math.sin

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 3_600_000L
private const val DEGREES_PER_TURN = 360f
private const val TICKS = 60
private const val MAJOR_EVERY = 5
private const val SUB_TICKS = 12

// Fractions of the dial radius. The digits carry the value, so the dial is decoration and needs no semantics.
private const val RIM_STROKE = 0.02f
private const val RIM_RADIUS = 0.98f
private const val TICK_OUTER = 0.95f
private const val MAJOR_TICK_INNER = 0.80f
private const val MINOR_TICK_INNER = 0.88f
private const val MAJOR_TICK_WIDTH = 0.04f
private const val MINOR_TICK_WIDTH = 0.015f
private const val HAND_LENGTH = 0.88f
private const val HAND_WIDTH = 0.035f
private const val HUB_RADIUS = 0.04f
private const val SUB_CENTER_OFFSET = 0.4f
private const val SUB_RADIUS = 0.22f
private const val SUB_HAND_LENGTH = 0.8f
private const val SUB_HAND_WIDTH = 0.06f

/**
 * The analogue face. The sweep hand is the seconds within the minute, and the small sub-dial is the minutes
 * within the hour. Both hands read [elapsed] continuously, so they move smoothly under a frame-driven caller.
 * Angles run clockwise from 12 o'clock, the way a stopwatch face is read.
 */
@Composable
internal fun StopwatchDial(elapsed: Duration, modifier: Modifier = Modifier) {
    val rim = MaterialTheme.colorScheme.outline
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    val hand = MaterialTheme.colorScheme.primary
    Canvas(modifier.clearAndSetSemantics {}) {
        val millis = elapsed.toMillis().coerceAtLeast(0L)
        val radius = size.minDimension / 2f
        drawRim(rim, radius)
        drawTicks(tick, center, radius, TICKS, MAJOR_EVERY)
        drawHand(
            color = hand,
            center = center,
            degrees = (millis % MILLIS_PER_MINUTE) / MILLIS_PER_MINUTE.toFloat() * DEGREES_PER_TURN,
            length = radius * HAND_LENGTH,
            width = radius * HAND_WIDTH,
        )
        drawHub(hand, center, radius * HUB_RADIUS)

        val subCenter = Offset(center.x, center.y - radius * SUB_CENTER_OFFSET)
        val subRadius = radius * SUB_RADIUS
        drawCircle(color = rim, radius = subRadius, center = subCenter, style = Stroke(width = radius * RIM_STROKE))
        drawTicks(tick, subCenter, subRadius, SUB_TICKS, majorEvery = 1)
        drawHand(
            color = hand,
            center = subCenter,
            degrees = (millis % MILLIS_PER_HOUR) / MILLIS_PER_HOUR.toFloat() * DEGREES_PER_TURN,
            length = subRadius * SUB_HAND_LENGTH,
            width = radius * SUB_HAND_WIDTH,
        )
        drawHub(hand, subCenter, radius * HUB_RADIUS)
    }
}

private fun DrawScope.drawRim(color: Color, radius: Float) {
    drawCircle(color = color, radius = radius * RIM_RADIUS, center = center, style = Stroke(width = radius * RIM_STROKE))
}

/** [count] ticks around [center]; every [majorEvery]th one is longer and thicker. */
private fun DrawScope.drawTicks(color: Color, center: Offset, radius: Float, count: Int, majorEvery: Int) {
    for (i in 0 until count) {
        val major = i % majorEvery == 0
        val (dx, dy) = unitVector(i.toFloat() / count * DEGREES_PER_TURN)
        val inner = if (major) MAJOR_TICK_INNER else MINOR_TICK_INNER
        drawLine(
            color = color,
            start = Offset(center.x + dx * radius * inner, center.y + dy * radius * inner),
            end = Offset(center.x + dx * radius * TICK_OUTER, center.y + dy * radius * TICK_OUTER),
            strokeWidth = radius * (if (major) MAJOR_TICK_WIDTH else MINOR_TICK_WIDTH),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawHand(color: Color, center: Offset, degrees: Float, length: Float, width: Float) {
    val (dx, dy) = unitVector(degrees)
    drawLine(
        color = color,
        start = center,
        end = Offset(center.x + dx * length, center.y + dy * length),
        strokeWidth = width,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawHub(color: Color, center: Offset, radius: Float) {
    drawCircle(color = color, radius = radius, center = center)
}

/** Clockwise from 12 o'clock: 0 degrees points up, 90 points right. Screen y grows downwards, hence the sign. */
private fun unitVector(degrees: Float): Pair<Float, Float> {
    val radians = Math.toRadians(degrees.toDouble() - 90.0)
    return Pair(cos(radians).toFloat(), sin(radians).toFloat())
}
