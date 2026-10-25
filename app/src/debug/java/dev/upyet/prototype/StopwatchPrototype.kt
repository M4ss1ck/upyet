// PROTOTYPE for #13, throwaway: three stopwatch screens, switchable from the floating pill. No ViewModels,
// no persistence, no Hilt - one in-memory fake stopwatch so the three layouts can be judged on their own.
package dev.upyet.prototype

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * #13, "How should the timer and stopwatch screens behave?" - the stopwatch half.
 *
 * All three variants run the same fake stopwatch (start/pause, lap, reset, six seeded laps) and differ in
 * how the laps and the controls are laid out: a table of laps under big digits (A), an analog dial (B), and
 * lap cards showing the delta against the previous lap with a fixed bottom bar (C).
 */
@Composable
internal fun StopwatchPrototype() {
    val lab = remember { StopwatchLab() }
    // Display only: this animates the centisecond readout for whoever is judging the layout. A stopwatch
    // is never an alarm and never schedules anything.
    LaunchedEffect(lab.running) {
        if (!lab.running) return@LaunchedEffect
        var lastFrame = withFrameMillis { it }
        while (true) {
            withFrameMillis { frame ->
                lab.advance(frame - lastFrame)
                lastFrame = frame
            }
        }
    }
    val variants =
        listOf(
            PrototypeVariant("A", "Big digits + table") { StopwatchVariantA(lab) },
            PrototypeVariant("B", "Dial") { StopwatchVariantB(lab) },
            PrototypeVariant("C", "Lap cards + delta") { StopwatchVariantC(lab) },
        )
    PrototypeVariants(variants = variants, modifier = Modifier.statusBarsPadding()) {
        PrototypeControlChip(
            label = "Seed 6 laps",
            checked = lab.isSeeded,
            onCheckedChange = { on -> if (on) lab.seedSixLaps() else lab.reset() },
        )
    }
}

// region Shared fake model

private data class PrototypeLap(val number: Int, val splitMillis: Long, val totalMillis: Long)

/** Far faster or slower than the others is readable without colour: the row also carries a text tag. */
private val fastestLapColor = Color(0xFF2E7D32)

private class StopwatchLab {
    var elapsedMillis by mutableLongStateOf(0L)
    var running by mutableStateOf(false)
    val laps = mutableStateListOf<PrototypeLap>()
    private var lastLapTotalMillis = 0L

    val isSeeded: Boolean get() = laps.size == 6

    /** Nothing to reset: reset is offered only once there is something on the clock. */
    val isPristine: Boolean get() = elapsedMillis == 0L && laps.isEmpty()

    fun toggleRun() {
        running = !running
    }

    fun advance(deltaMillis: Long) {
        if (!running) return
        elapsedMillis = (elapsedMillis + deltaMillis).coerceAtLeast(0L)
    }

    fun lap() {
        if (!running) return
        val split = elapsedMillis - lastLapTotalMillis
        laps.add(PrototypeLap(number = laps.size + 1, splitMillis = split, totalMillis = elapsedMillis))
        lastLapTotalMillis = elapsedMillis
    }

    /** Reset is a paused-only action, exactly like the real Stopwatch. */
    fun reset() {
        if (running) return
        elapsedMillis = 0L
        lastLapTotalMillis = 0L
        laps.clear()
    }

    /** Fake laps, so the ranking and the deltas have something to show without waiting a real minute. */
    fun seedSixLaps() {
        val splits = listOf(48_120L, 45_900L, 52_340L, 44_760L, 47_010L, 51_220L)
        laps.clear()
        lastLapTotalMillis = 0L
        splits.forEach { split ->
            lastLapTotalMillis += split
            laps.add(PrototypeLap(number = laps.size + 1, splitMillis = split, totalMillis = lastLapTotalMillis))
        }
        elapsedMillis = lastLapTotalMillis
        running = false
    }
}

/** Laps rank against each other only when there is something to rank. */
private fun rankedLaps(laps: List<PrototypeLap>): Pair<Long, Long>? {
    if (laps.size < 2) return null
    val fastest = laps.minOf { it.splitMillis }
    val slowest = laps.maxOf { it.splitMillis }
    return if (fastest == slowest) null else fastest to slowest
}

// endregion

// region Variant A - big digits, big buttons, laps as a table

@Composable
private fun StopwatchVariantA(lab: StopwatchLab) {
    val laps = lab.laps
    val ranking = rankedLaps(laps)
    Column(Modifier.fillMaxSize()) {
        PrototypeBareTitle(title = "Stopwatch", subtitle = "A · Big digits, laps as a table")
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = formatStopwatch(lab.elapsedMillis), style = MaterialTheme.typography.displayLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { lab.toggleRun() }, modifier = Modifier.height(56.dp)) {
                    Text(
                        text = if (lab.running) "Pause" else "Start",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                OutlinedButton(onClick = { lab.lap() }, enabled = lab.running) { Text(text = "Lap") }
                OutlinedButton(onClick = { lab.reset() }, enabled = !lab.running && !lab.isPristine) { Text(text = "Reset") }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text(text = "#", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(36.dp))
            Text(text = "Lap", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(
                text = "Total",
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
        ) {
            // Newest on top: the lap just taken is the one being looked at.
            items(laps.asReversed(), key = { it.number }) { lap ->
                LapRowA(lap = lap, ranking = ranking)
            }
        }
    }
}

@Composable
private fun LapRowA(lap: PrototypeLap, ranking: Pair<Long, Long>?) {
    val fastest = ranking?.first
    val slowest = ranking?.second
    val color = when {
        fastest != null && lap.splitMillis == fastest -> fastestLapColor
        slowest != null && lap.splitMillis == slowest -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val tag = when {
        fastest != null && lap.splitMillis == fastest -> "fastest"
        slowest != null && lap.splitMillis == slowest -> "slowest"
        else -> null
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${lap.number}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp),
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = formatStopwatch(lap.splitMillis), style = MaterialTheme.typography.titleMedium, color = color)
            if (tag != null) PrototypeTag(text = tag, color = color)
        }
        Text(
            text = formatStopwatch(lap.totalMillis),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

// endregion

// region Variant B - an analog dial

@Composable
private fun StopwatchVariantB(lab: StopwatchLab) {
    val laps = lab.laps
    Column(Modifier.fillMaxSize()) {
        PrototypeBareTitle(title = "Stopwatch", subtitle = "B · Analog dial, laps oldest first")
        // One scroll container for the whole screen: the dial block scrolls away once six laps need the
        // room, and a stopwatch with no laps still fills most of the height instead of the bottom half.
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 0.88 of the window width: the dial is the screen, the buttons just sit under it.
                    StopwatchDial(
                        elapsedMillis = lab.elapsedMillis,
                        modifier = Modifier.fillMaxWidth(0.88f).aspectRatio(1f),
                    )
                    Text(
                        text = formatStopwatch(lab.elapsedMillis),
                        style = MaterialTheme.typography.displayMedium.tabular(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundButton(text = "Lap", enabled = lab.running) { lab.lap() }
                        RoundButton(text = if (lab.running) "Pause" else "Start", primary = true) { lab.toggleRun() }
                        RoundButton(text = "Reset", enabled = !lab.running && !lab.isPristine) { lab.reset() }
                    }
                }
                Text(
                    text = "Laps · oldest first",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
                )
                HorizontalDivider()
            }
            items(laps, key = { it.number }) { lap -> CompactLapRow(lap = lap) }
        }
    }
}

/** Fixed-width digits: a running stopwatch must not shuffle sideways as the numbers change. */
private fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/** Seconds sweep on the big dial, minutes on the small sub-dial: no text, just where the hand is. */
@Composable
private fun StopwatchDial(elapsedMillis: Long, modifier: Modifier = Modifier) {
    val dialColor = MaterialTheme.colorScheme.onSurface
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val faceColor = MaterialTheme.colorScheme.surface
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        // Every dimension is a fraction of the radius, so the dial keeps its proportions when it grows
        // from phone to phone instead of turning into hairline ticks on a big circle.
        val rim = radius * 0.026f
        // Inset by half the stroke: a rim drawn at the exact radius loses its outer half to the bounds.
        drawCircle(color = trackColor, radius = radius - rim / 2f, center = center, style = Stroke(width = rim))
        repeat(60) { index ->
            val radians = (index * 6.0 - 90.0) * PI / 180.0
            val major = index % 5 == 0
            val outerRadius = radius - radius * 0.086f
            val innerRadius = outerRadius - radius * if (major) 0.12f else 0.06f
            drawLine(
                color = if (major) dialColor else trackColor,
                start = center + Offset(cos(radians).toFloat() * innerRadius, sin(radians).toFloat() * innerRadius),
                end = center + Offset(cos(radians).toFloat() * outerRadius, sin(radians).toFloat() * outerRadius),
                strokeWidth = radius * if (major) 0.026f else 0.017f,
                cap = StrokeCap.Round,
            )
        }
        // The sub-dial is opaque and drawn before the sweep hand, the way a real chronograph keeps its
        // minute register readable while the seconds hand passes over it.
        val subCenter = center + Offset(0f, -radius * 0.44f)
        val subRadius = radius * 0.24f
        drawCircle(color = faceColor, radius = subRadius, center = subCenter)
        drawCircle(color = trackColor, radius = subRadius, center = subCenter, style = Stroke(width = radius * 0.017f))
        val minuteFraction = (elapsedMillis / 60_000L % 60L) / 60f
        val minuteRadians = (minuteFraction * 360f - 90f) * (PI / 180.0)
        drawLine(
            color = dialColor,
            start = subCenter,
            end =
            subCenter +
                Offset(
                    cos(minuteRadians).toFloat() * subRadius * 0.8f,
                    sin(minuteRadians).toFloat() * subRadius * 0.8f,
                ),
            strokeWidth = radius * 0.017f,
            cap = StrokeCap.Round,
        )
        val secondFraction = (elapsedMillis % 60_000L) / 60_000f
        val secondRadians = (secondFraction * 360f - 90f) * (PI / 180.0)
        val handLength = radius - radius * 0.18f
        drawLine(
            color = accent,
            start = center,
            end = center + Offset(cos(secondRadians).toFloat() * handLength, sin(secondRadians).toFloat() * handLength),
            strokeWidth = radius * 0.034f,
            cap = StrokeCap.Round,
        )
        drawCircle(color = accent, radius = radius * 0.05f, center = center)
    }
}

@Composable
private fun RoundButton(text: String, enabled: Boolean = true, primary: Boolean = false, onClick: () -> Unit) {
    val container = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier =
        Modifier
            .size(if (primary) 104.dp else 84.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = if (enabled) container else container.copy(alpha = 0.4f),
        contentColor = if (enabled) content else content.copy(alpha = 0.5f),
        // The secondary discs are surfaceVariant on a Mist background, i.e. invisible without an edge:
        // at 84 dp the outline is what makes Lap and Reset read as round buttons at all.
        border = if (primary) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 1f else 0.4f))
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CompactLapRow(lap: PrototypeLap) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Lap ${lap.number}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = formatStopwatch(lap.splitMillis),
            style = MaterialTheme.typography.titleLarge.tabular(),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatStopwatch(lap.totalMillis),
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

// endregion

// region Variant C - lap cards with deltas and a fixed bottom bar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopwatchVariantC(lab: StopwatchLab) {
    val laps = lab.laps
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text = "Stopwatch", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "C · Lap cards, delta against the lap before",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Reset lives in the top bar's overflow here: the bottom bar belongs to Lap and Start.
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Text(text = "⋮", style = MaterialTheme.typography.titleLarge)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(text = "Reset") },
                            enabled = !lab.running && !lab.isPristine,
                            onClick = {
                                lab.reset()
                                menuOpen = false
                            },
                        )
                    }
                }
            }
            Text(
                text = formatStopwatch(lab.elapsedMillis),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                // Clears the bottom bar the layout itself pins here, not the prototype pill.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(laps.asReversed(), key = { it.number }) { lap ->
                    LapCardC(lap = lap, previousSplitMillis = laps.getOrNull(lap.number - 2)?.splitMillis)
                }
            }
        }
        // The bar is fixed to the bottom of the content: Lap and Start belong at the thumb, and the list
        // above it keeps its own clearance so neither hides the other.
        Row(
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { lab.lap() },
                enabled = lab.running,
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Text(text = "Lap")
            }
            Button(
                onClick = { lab.toggleRun() },
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Text(text = if (lab.running) "Pause" else "Start")
            }
        }
    }
}

@Composable
private fun LapCardC(lap: PrototypeLap, previousSplitMillis: Long?) {
    val delta = previousSplitMillis?.let { lap.splitMillis - it }
    val deltaColor = when {
        delta == null -> MaterialTheme.colorScheme.onSurfaceVariant
        delta < 0 -> fastestLapColor
        else -> MaterialTheme.colorScheme.error
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Lap ${lap.number}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = formatStopwatch(lap.splitMillis), style = MaterialTheme.typography.headlineSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (delta == null) "first lap" else "${formatLapDelta(delta)} vs previous",
                    style = MaterialTheme.typography.bodyMedium,
                    color = deltaColor,
                )
                Text(
                    text = "total ${formatStopwatch(lap.totalMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// endregion
