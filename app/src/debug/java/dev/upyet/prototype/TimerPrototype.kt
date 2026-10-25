// PROTOTYPE for #13, throwaway: three timer screens, switchable from the floating pill. No ViewModels,
// no Room, no DataStore, no Hilt - one in-memory fake list so the three layouts can be judged on their own.
package dev.upyet.prototype

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * #13, "How should the timer and stopwatch screens behave?" - the timer half.
 *
 * The three variants answer the same product question - where does a countdown live when there can be
 * several of them, and where does the per-timer **Timer evidence** toggle (ADR 0010) sit? - and differ in
 * structure: a keypad plus cards (A), wheels plus a dense list with no sheets at all (B), and one timer per
 * pager page (C). The fake model, the ticking and the evidence rules are shared on purpose.
 */
@Composable
internal fun TimerPrototype() {
    val lab = remember { TimerLab() }
    // Display only. This ticker exists so a reviewer can watch a countdown move; real timers are driven by
    // stored anchors and one exact alarm (ADR 0008) and nothing here schedules anything.
    LaunchedEffect(lab) {
        while (true) {
            delay(1_000)
            lab.tick()
        }
    }
    val anyRinging = lab.timers.any { it.status == TimerStatus.Ringing }
    val variants =
        listOf(
            PrototypeVariant("A", "Keypad + cards") { TimerVariantA(lab) },
            PrototypeVariant("B", "Wheels + presets") { TimerVariantB(lab) },
            PrototypeVariant("C", "One timer per page") { TimerVariantC(lab) },
        )
    PrototypeVariants(variants = variants, modifier = Modifier.statusBarsPadding()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // A default for *new* timers only: flipping it never changes a timer that already exists.
            PrototypeControlChip(
                label = "Global evidence",
                checked = lab.globalEvidence,
                onCheckedChange = { lab.globalEvidence = it },
            )
            // Rings the first running timer so the "no Pause, evidence locked" state is one tap away.
            PrototypeControlChip(
                label = "Ring one",
                checked = anyRinging,
                onCheckedChange = { on -> if (on) lab.ringFirstRunning() else lab.stopAllRinging() },
            )
        }
    }
}

// region Shared fake model

/** The five states of ADR 0008, named after what the user sees. */
private enum class TimerStatus { Idle, Running, Paused, Ringing, Missed }

private data class PrototypeTimer(
    val id: Int,
    val label: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val status: TimerStatus,
    val evidence: Boolean,
)

/** Remaining over total, 1 for a full Idle timer, 0 for one that is ringing or missed. */
private fun PrototypeTimer.progress(): Float = when (status) {
    TimerStatus.Running, TimerStatus.Paused -> if (totalSeconds == 0) 0f else remainingSeconds.toFloat() / totalSeconds
    TimerStatus.Idle -> 1f
    TimerStatus.Ringing, TimerStatus.Missed -> 0f
}

/**
 * One in-memory fake list shared by all three variants, seeded with a running, a paused and a missed timer
 * so every variant shows all three at once. Every mutation is a whole-data-class copy, which is exactly what
 * the real state machine does with its anchors.
 */
private class TimerLab {
    private var nextId = 10
    val timers =
        mutableStateListOf(
            PrototypeTimer(
                id = 1,
                label = "Pasta",
                totalSeconds = 600,
                remainingSeconds = 462,
                status = TimerStatus.Running,
                evidence = true,
            ),
            // Deliberately unlabelled: the ringing screen has to read well without a label too.
            PrototypeTimer(
                id = 2,
                label = "",
                totalSeconds = 300,
                remainingSeconds = 130,
                status = TimerStatus.Paused,
                evidence = false,
            ),
            PrototypeTimer(
                id = 3,
                label = "Laundry",
                totalSeconds = 1500,
                remainingSeconds = 0,
                status = TimerStatus.Missed,
                evidence = false,
            ),
        )

    /** The Settings default, off by default. It feeds new timers only. */
    var globalEvidence by mutableStateOf(false)

    /** Start creates a running timer at its full length; it never sits Idle first. */
    fun create(totalSeconds: Int, label: String, evidence: Boolean = globalEvidence) {
        if (totalSeconds <= 0) return
        timers.add(
            0,
            PrototypeTimer(
                id = nextId++,
                label = label.trim(),
                totalSeconds = totalSeconds,
                remainingSeconds = totalSeconds,
                status = TimerStatus.Running,
                evidence = evidence,
            ),
        )
    }

    fun tick() {
        for (index in timers.indices) {
            val timer = timers[index]
            if (timer.status != TimerStatus.Running) continue
            val remaining = (timer.remainingSeconds - 1).coerceAtLeast(0)
            timers[index] =
                timer.copy(
                    remainingSeconds = remaining,
                    status = if (remaining == 0) TimerStatus.Ringing else TimerStatus.Running,
                )
        }
    }

    fun togglePause(id: Int) = mutate(id) { timer ->
        when (timer.status) {
            TimerStatus.Running -> timer.copy(status = TimerStatus.Paused)
            TimerStatus.Paused -> timer.copy(status = TimerStatus.Running)
            TimerStatus.Idle -> timer.copy(status = TimerStatus.Running, remainingSeconds = timer.totalSeconds)
            TimerStatus.Ringing, TimerStatus.Missed -> timer
        }
    }

    /** Offered while Running, Paused, Ringing and Missed; never on an Idle timer (ADR 0008). */
    fun addMinute(id: Int) = mutate(id) { timer ->
        when (timer.status) {
            TimerStatus.Running, TimerStatus.Paused -> timer.copy(remainingSeconds = timer.remainingSeconds + 60)
            TimerStatus.Ringing, TimerStatus.Missed -> timer.copy(remainingSeconds = 60, status = TimerStatus.Running)
            TimerStatus.Idle -> timer
        }
    }

    /** Stop, Reset and Dismiss all land on Idle at the original length; the timer stays in the list. */
    fun reset(id: Int) = mutate(id) { it.copy(remainingSeconds = it.totalSeconds, status = TimerStatus.Idle) }

    fun delete(id: Int) {
        val index = timers.indexOfFirst { it.id == id }
        if (index >= 0) timers.removeAt(index)
    }

    fun setLabel(id: Int, label: String) = mutate(id) { it.copy(label = label) }

    /** Editable until it rings; a ringing timer keeps whatever it had (ADR 0010). */
    fun setEvidence(id: Int, evidence: Boolean) = mutate(id) { timer ->
        if (timer.status == TimerStatus.Ringing) timer else timer.copy(evidence = evidence)
    }

    fun ringFirstRunning() {
        val index = timers.indexOfFirst { it.status == TimerStatus.Running }
        if (index >= 0) timers[index] = timers[index].copy(status = TimerStatus.Ringing, remainingSeconds = 0)
    }

    fun stopAllRinging() {
        for (index in timers.indices) {
            if (timers[index].status == TimerStatus.Ringing) {
                timers[index] = timers[index].copy(status = TimerStatus.Idle, remainingSeconds = timers[index].totalSeconds)
            }
        }
    }

    private fun mutate(id: Int, transform: (PrototypeTimer) -> PrototypeTimer) {
        val index = timers.indexOfFirst { it.id == id }
        if (index >= 0) timers[index] = transform(timers[index])
    }
}

/** Keys of the keypad, in the order they render: 1-9, then backspace, 0 and "00". */
private val timerKeypadRows =
    listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("⌫", "0", "00"),
    )

/** Digits are entered right-to-left into hh mm ss, the way every phone's clock app does it. */
private fun keypadPress(digits: String, key: String): String = when {
    key == "⌫" -> digits.dropLast(1)
    key == "00" -> if (digits.length <= 4) digits + "00" else digits
    digits.length < 6 -> digits + key
    else -> digits
}

private fun keypadSeconds(digits: String): Int {
    val value = digits.toIntOrNull() ?: 0
    return (value / 10000) * 3600 + ((value / 100) % 100) * 60 + (value % 100)
}

@Composable
private fun timerStatusColor(status: TimerStatus): Color = when (status) {
    TimerStatus.Ringing, TimerStatus.Missed -> MaterialTheme.colorScheme.error
    TimerStatus.Paused -> MaterialTheme.colorScheme.secondary
    TimerStatus.Running -> MaterialTheme.colorScheme.primary
    TimerStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun TimerStatusTag(status: TimerStatus) {
    PrototypeTag(text = status.name, color = timerStatusColor(status))
}

/** A thin arc: remaining time, so the ring empties as the countdown does. */
@Composable
private fun TimerProgressRing(progress: Float, color: Color, modifier: Modifier = Modifier, strokeWidth: Dp = 6.dp) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val stroke = strokeWidth.toPx()
        val diameter = size.minDimension - stroke
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** The evidence control, in the one wording the product uses for it. */
@Composable
private fun TimerEvidenceRow(timer: PrototypeTimer, lab: TimerLab) {
    val editable = timer.status != TimerStatus.Ringing
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text = "Timer evidence", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (editable) {
                    "Records this timer's ring. Defaults from Settings at creation."
                } else {
                    "Locked while the timer rings: Stop, Stop all and +1 min only."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = timer.evidence, onCheckedChange = { lab.setEvidence(timer.id, it) }, enabled = editable)
    }
}

// endregion

// region Variant A - keypad when empty, cards when not, sheets for editing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerVariantA(lab: TimerLab) {
    var showKeypad by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableIntStateOf(0) }
    var digits by rememberSaveable { mutableStateOf("") }
    val editing = lab.timers.firstOrNull { it.id == editingId }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PrototypeBareTitle(title = "Timer", subtitle = "A · DeskClock keypad, one card per timer")
            if (lab.timers.isEmpty()) {
                // The empty state is the whole screen: nothing else to look at, so the keypad is the page.
                TimerEntryPad(digits = digits, onDigits = { digits = it }, modifier = Modifier.weight(1f))
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Start a timer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    ExtendedFloatingActionButton(
                        onClick = {
                            lab.create(keypadSeconds(digits), "")
                            digits = ""
                        },
                        icon = { Text(text = "▶", style = MaterialTheme.typography.titleMedium) },
                        text = { Text("Start") },
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        top = 4.dp,
                        end = 16.dp,
                        // Clears the FAB, not the prototype pill: 56 dp of button plus the two margins.
                        bottom = 88.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(lab.timers, key = { it.id }) { timer ->
                        TimerCardA(timer = timer, lab = lab, onEdit = { editingId = timer.id })
                    }
                }
            }
        }
        if (lab.timers.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showKeypad = true },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 16.dp),
            ) {
                Text(text = "+", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }

    if (showKeypad) {
        ModalBottomSheet(onDismissRequest = { showKeypad = false }) {
            TimerEntryPad(digits = digits, onDigits = { digits = it })
            Button(
                onClick = {
                    lab.create(keypadSeconds(digits), "")
                    digits = ""
                    showKeypad = false
                },
                enabled = keypadSeconds(digits) > 0,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(text = "Start")
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (editing != null) {
        ModalBottomSheet(onDismissRequest = { editingId = 0 }) {
            val summary =
                "${formatCountdown(editing.remainingSeconds)} of " +
                    "${formatCountdown(editing.totalSeconds)} · ${editing.status.name}"
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Timer", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = editing.label,
                    onValueChange = { lab.setLabel(editing.id, it) },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TimerEvidenceRow(timer = editing, lab = lab)
                HorizontalDivider()
                TextButton(onClick = {
                    lab.delete(editing.id)
                    editingId = 0
                }) { Text(text = "Delete timer") }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TimerEntryPad(digits: String, onDigits: (String) -> Unit, modifier: Modifier = Modifier) {
    // Padded to six places so the three fields are always visible while typing right-to-left.
    val padded = digits.padStart(6, '0')
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${padded.substring(0, 2)}:${padded.substring(2, 4)}:${padded.substring(4, 6)}",
            style = MaterialTheme.typography.displayMedium,
        )
        Text(
            text = "hours · minutes · seconds",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            timerKeypadRows.forEach { keys ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.forEach { key ->
                        OutlinedButton(onClick = { onDigits(keypadPress(digits, key)) }, modifier = Modifier.weight(1f)) {
                            Text(text = key, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerCardA(timer: PrototypeTimer, lab: TimerLab, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TimerProgressRing(
                    progress = timer.progress(),
                    color = timerStatusColor(timer.status),
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 5.dp,
                )
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = formatCountdown(timer.remainingSeconds), style = MaterialTheme.typography.headlineSmall)
                        TimerStatusTag(timer.status)
                        if (timer.evidence) PrototypeTag(text = "REC", color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        text = timer.label.ifEmpty { "No label" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                TimerCardActionsA(timer = timer, lab = lab)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { lab.delete(timer.id) }) { Text(text = "✕") }
            }
            if (timer.status == TimerStatus.Ringing) {
                Text(
                    text = "Pause is not offered while ringing, and Timer evidence is locked.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TimerCardActionsA(timer: PrototypeTimer, lab: TimerLab) {
    when (timer.status) {
        TimerStatus.Ringing -> {
            TextButton(onClick = { lab.reset(timer.id) }) { Text(text = "Stop") }
            TextButton(onClick = { lab.addMinute(timer.id) }) { Text(text = "+1 min") }
        }

        TimerStatus.Missed -> TextButton(onClick = { lab.reset(timer.id) }) { Text(text = "Dismiss") }

        TimerStatus.Idle -> TextButton(onClick = { lab.togglePause(timer.id) }) { Text(text = "Start") }

        TimerStatus.Running, TimerStatus.Paused -> {
            TextButton(onClick = { lab.togglePause(timer.id) }) {
                Text(text = if (timer.status == TimerStatus.Paused) "Resume" else "Pause")
            }
            TextButton(onClick = { lab.addMinute(timer.id) }) { Text(text = "+1 min") }
            TextButton(onClick = { lab.reset(timer.id) }) { Text(text = "Reset") }
        }
    }
}

// endregion

// region Variant B - wheels, presets and a dense list, no sheets

@Composable
private fun TimerVariantB(lab: TimerLab) {
    var hours by rememberSaveable { mutableIntStateOf(0) }
    var minutes by rememberSaveable { mutableIntStateOf(0) }
    var seconds by rememberSaveable { mutableIntStateOf(0) }
    // Bumped by a preset so the wheels re-mount at the new value instead of fighting the scroll state.
    var wheelEpoch by rememberSaveable { mutableIntStateOf(0) }
    var label by rememberSaveable { mutableStateOf("") }
    var recordThisTimer by rememberSaveable { mutableStateOf(lab.globalEvidence) }
    // The inline switch above is this new timer's own toggle; it never rewrites the Settings default.
    val total = hours * 3600 + minutes * 60 + seconds

    Column(Modifier.fillMaxSize()) {
        PrototypeBareTitle(title = "Timer", subtitle = "B · Wheels and presets, everything inline")
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Top,
                    ) {
                        key(wheelEpoch) {
                            TimerWheel(value = hours, count = 24, caption = "h") { hours = it }
                            WheelSeparator()
                            TimerWheel(value = minutes, count = 60, caption = "m") { minutes = it }
                            WheelSeparator()
                            TimerWheel(value = seconds, count = 60, caption = "s") { seconds = it }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(1, 3, 5, 10, 15, 30).forEach { presetMinutes ->
                            OutlinedButton(
                                onClick = {
                                    hours = 0
                                    minutes = presetMinutes
                                    seconds = 0
                                    wheelEpoch++
                                },
                            ) {
                                Text(text = "$presetMinutes min")
                            }
                        }
                    }
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Label (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(text = "Record when it rings", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "This new timer only. Settings holds the default.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = recordThisTimer, onCheckedChange = { recordThisTimer = it })
                    }
                    Button(
                        onClick = {
                            lab.create(total, label, recordThisTimer)
                            label = ""
                            hours = 0
                            minutes = 0
                            seconds = 0
                            wheelEpoch++
                        },
                        enabled = total > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Start ${formatCountdown(total)}")
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
            }
            if (lab.timers.isEmpty()) {
                item {
                    Text(
                        text = "No timers. Set one above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            items(lab.timers, key = { it.id }) { timer -> DenseTimerRow(timer = timer, lab = lab) }
        }
    }
}

private val wheelItemHeight = 36.dp

/** A three-slot snapping wheel: the centred row is the value, and it reports every centred change up. */
@Composable
private fun TimerWheel(value: Int, count: Int, caption: String, onValueChange: (Int) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = value.coerceIn(0, count - 1))
    // The scroll position changes on every pixel of a fling; deriving the centred row keeps recomposition
    // down to actual value changes instead of every scroll offset.
    val selectedIndex by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex.coerceIn(0, count - 1) }
    }
    LaunchedEffect(selectedIndex) { onValueChange(selectedIndex) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LazyColumn(
            state = state,
            modifier = Modifier.height(wheelItemHeight * 3).width(64.dp),
            // Half-item padding on both ends centres the trailing items, so the item at
            // firstVisibleItemIndex is exactly the one under the selection line.
            contentPadding = PaddingValues(vertical = wheelItemHeight),
            flingBehavior = rememberSnapFlingBehavior(state),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(count) { index ->
                Box(Modifier.height(wheelItemHeight), contentAlignment = Alignment.Center) {
                    Text(
                        text = index.toString().padStart(2, '0'),
                        style = if (index == selectedIndex) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        color = if (index == selectedIndex) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WheelSeparator() {
    Text(
        text = ":",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = wheelItemHeight, start = 2.dp, end = 2.dp),
    )
}

@Composable
private fun DenseTimerRow(timer: PrototypeTimer, lab: TimerLab) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timer.label.ifEmpty { "No label" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                TimerStatusTag(timer.status)
            }
            Text(text = formatCountdown(timer.remainingSeconds), style = MaterialTheme.typography.titleMedium)
        }
        TimerRowActionsB(timer = timer, lab = lab)
        TimerEvidencePill(timer = timer, lab = lab)
        IconButton(onClick = { lab.delete(timer.id) }) { Text(text = "✕") }
    }
}

@Composable
private fun TimerRowActionsB(timer: PrototypeTimer, lab: TimerLab) {
    when (timer.status) {
        TimerStatus.Ringing -> {
            SmallGlyphButton("Stop") { lab.reset(timer.id) }
            SmallGlyphButton("+1") { lab.addMinute(timer.id) }
        }

        TimerStatus.Missed -> SmallGlyphButton("Dismiss") { lab.reset(timer.id) }

        TimerStatus.Idle -> SmallGlyphButton("▶") { lab.togglePause(timer.id) }

        TimerStatus.Running, TimerStatus.Paused -> {
            SmallGlyphButton(if (timer.status == TimerStatus.Paused) "▶" else "❚❚") { lab.togglePause(timer.id) }
            SmallGlyphButton("+1") { lab.addMinute(timer.id) }
            SmallGlyphButton("↺") { lab.reset(timer.id) }
        }
    }
}

/** The per-timer evidence toggle in its smallest form: a chip, dimmed and inert while ringing. */
@Composable
private fun TimerEvidencePill(timer: PrototypeTimer, lab: TimerLab) {
    val editable = timer.status != TimerStatus.Ringing
    val color = if (timer.evidence) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier =
        Modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = editable) { lab.setEvidence(timer.id, !timer.evidence) },
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = if (editable) 0.14f else 0.06f),
        contentColor = color.copy(alpha = if (editable) 1f else 0.4f),
    ) {
        Text(
            text = if (timer.evidence) "REC" else "rec",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

/**
 * A compact text-glyph action. It is sized to its own glyph instead of a fixed square, because the Missed
 * row's action is the word "Dismiss": a 36 dp IconButton box squeezed it onto two lines. Only the label and
 * time column gives up width, and the glyph itself never wraps.
 */
@Composable
private fun SmallGlyphButton(glyph: String, onClick: () -> Unit) {
    Box(
        modifier =
        Modifier
            .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// endregion

// region Variant C - one timer per pager page

@Composable
private fun TimerVariantC(lab: TimerLab) {
    val pageCount = lab.timers.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        PrototypeBareTitle(title = "Timer", subtitle = "C · One timer per page, new timer last")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(pageCount) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.3f,
                                )
                            },
                        ),
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            if (page < lab.timers.size) {
                TimerPageC(timer = lab.timers[page], lab = lab)
            } else {
                NewTimerPageC(lab = lab, onCreated = { scope.launch { pagerState.animateScrollToPage(0) } })
            }
        }
    }
}

@Composable
private fun TimerPageC(timer: PrototypeTimer, lab: TimerLab) {
    var editingLabel by rememberSaveable(timer.id) { mutableStateOf(false) }
    val editableEvidence = timer.status != TimerStatus.Ringing
    Column(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TimerStatusTag(timer.status)
            if (timer.evidence) PrototypeTag(text = "REC", color = MaterialTheme.colorScheme.primary)
        }
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            TimerProgressRing(
                progress = timer.progress(),
                color = timerStatusColor(timer.status),
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 14.dp,
            )
            Text(
                text = formatCountdown(timer.remainingSeconds),
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when (timer.status) {
                TimerStatus.Ringing -> {
                    Button(onClick = { lab.reset(timer.id) }) { Text(text = "Stop") }
                    OutlinedButton(onClick = { lab.addMinute(timer.id) }) { Text(text = "+1 min") }
                }

                TimerStatus.Missed -> Button(onClick = { lab.reset(timer.id) }) { Text(text = "Dismiss") }

                TimerStatus.Idle -> Button(onClick = { lab.togglePause(timer.id) }) { Text(text = "Start") }

                TimerStatus.Running -> {
                    Button(onClick = { lab.togglePause(timer.id) }) { Text(text = "Pause") }
                    OutlinedButton(onClick = { lab.addMinute(timer.id) }) { Text(text = "+1 min") }
                    OutlinedButton(onClick = { lab.reset(timer.id) }) { Text(text = "Reset") }
                }

                TimerStatus.Paused -> {
                    Button(onClick = { lab.togglePause(timer.id) }) { Text(text = "Resume") }
                    OutlinedButton(onClick = { lab.addMinute(timer.id) }) { Text(text = "+1 min") }
                    OutlinedButton(onClick = { lab.reset(timer.id) }) { Text(text = "Reset") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TappableChip(text = timer.label.ifEmpty { "Add label" }, active = timer.label.isNotEmpty()) {
                editingLabel = !editingLabel
            }
            TappableChip(
                text = if (timer.evidence) "Evidence on" else "Evidence off",
                active = timer.evidence,
                enabled = editableEvidence,
            ) {
                lab.setEvidence(timer.id, !timer.evidence)
            }
        }
        if (editingLabel) {
            OutlinedTextField(
                value = timer.label,
                onValueChange = { lab.setLabel(timer.id, it) },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!editableEvidence) {
            Text(
                text = "Evidence is locked while this timer rings.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NewTimerPageC(lab: TimerLab, onCreated: () -> Unit) {
    var digits by rememberSaveable { mutableStateOf("") }
    val total = keypadSeconds(digits)
    Column(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "New timer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
        TimerEntryPad(digits = digits, onDigits = { digits = it }, modifier = Modifier.weight(1f))
        Button(
            onClick = {
                lab.create(total, "")
                digits = ""
                onCreated()
            },
            enabled = total > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Start")
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** A chip-shaped control: a label field or the evidence toggle, without a sheet or a screen of its own. */
@Composable
private fun TappableChip(text: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = if (enabled) 0.14f else 0.06f),
        contentColor = color.copy(alpha = if (enabled) 1f else 0.4f),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

// endregion
