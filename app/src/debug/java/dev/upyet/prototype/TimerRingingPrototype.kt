// PROTOTYPE for #14, throwaway: "What does a ringing timer look like?" - three screen structures, switchable
// from the floating pill. No ViewModels, no Room, no DataStore, no Hilt, no CameraX: one in-memory list of
// ringing timers and a display-only ticker, so the three layouts can be judged on their own.
package dev.upyet.prototype

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.upyet.core.ui.theme.RingingPalette
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * #14, "What does a ringing timer look like?" - the screen half.
 *
 * Every variant answers the same already-decided question - its own `TimerRingingActivity` over the lock
 * screen, several ringing timers on one screen, Stop and +1 min per timer, Stop all once two ring, never
 * Pause - and differs in structure: the alarm screen's skeleton with rows (A), one full card per timer (B),
 * one focused timer with the rest queued as chips (C). The fake model, the ticker and the evidence wording
 * are shared on purpose: the comparison is about where the two buttons per timer live.
 *
 * A +1 min removes the timer from the showing exactly like Stop (ADR 0010), so both call the same thing
 * here. Nothing below talks to a service, and every button works whatever the camera state says.
 */
@Composable
internal fun TimerRingingPrototype() {
    val lab = remember { RingingLab() }
    // Display only. A real ring is driven by the timer's stored end instant and one exact alarm (ADR 0008);
    // this ticker exists so a reviewer can watch the overtime counters and the recording clock move.
    LaunchedEffect(lab) {
        while (true) {
            delay(1_000)
            lab.tick()
        }
    }
    val variants =
        listOf(
            PrototypeVariant("A", "Alarm twin") { TimerRingingTwin(lab = lab) },
            PrototypeVariant("B", "Stacked cards") { TimerRingingStacked(lab = lab) },
            PrototypeVariant("C", "Focus + queue") { TimerRingingFocus(lab = lab) },
        )
    PrototypeVariants(variants = variants) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PrototypeCycleChip(label = "Ringing", value = lab.showing.size.toString()) { lab.cycleRinging(SEEDED_TIMERS) }
                PrototypeControlChip(
                    label = "Lock screen",
                    checked = lab.lockScreen,
                    onCheckedChange = { lab.lockScreen = it },
                )
            }
            PrototypeCycleChip(label = "Recording", value = lab.evidence.chip) { lab.evidence = lab.evidence.next() }
        }
    }
}

// region Shared fake model

/** How many ringing timers the fake data can put on screen at once. */
internal const val SEEDED_TIMERS = 3

/**
 * What the camera is doing, in the four states the ringing screen has to word differently. `CameraFailed`
 * and `BeforeUnlock` are still *recording-on* timers: their ring is an occurrence, the camera just could not
 * run (AGENTS §10, §11), so the wording changes while the buttons keep working.
 */
internal enum class EvidenceMode(val chip: String) {
    Off("off"),
    On("on"),
    CameraFailed("camera failed"),
    BeforeUnlock("before unlock"),
    ;

    fun next(): EvidenceMode = entries[(ordinal + 1) % entries.size]
}

/** The little a ringing screen needs to know about one timer: its name and its original length. */
internal data class PrototypeRingingTimer(val id: Int, val label: String, val totalSeconds: Int)

/** A timer with no label still has to read as something: the product word for it is "Timer". */
internal fun PrototypeRingingTimer.title(): String = label.ifBlank { "Timer" }

/** "−0:45": counting up since the timer ended. Display only; a real one comes from the stored end instant. */
internal fun formatOvertime(seconds: Int): String = "−" + formatCountdown(seconds)

/**
 * One in-memory showing shared by both #14 prototypes. The list is the showing itself: Stop, +1 min and
 * Stop all mutate it, and an empty list is the moment a real activity would close.
 */
internal class RingingLab {
    private val seed =
        listOf(
            PrototypeRingingTimer(id = 1, label = "Pasta", totalSeconds = 600),
            // Deliberately unlabelled: the row has to read well without a label too.
            PrototypeRingingTimer(id = 2, label = "", totalSeconds = 300),
            PrototypeRingingTimer(id = 3, label = "Laundry", totalSeconds = 1500),
        )

    /** The timers ringing right now, oldest ring first. */
    val showing = mutableStateListOf<PrototypeRingingTimer>().apply { addAll(seed) }

    /** The camera state the screen words its status from. Flips with the "Recording" chip. */
    var evidence by mutableStateOf(EvidenceMode.Off)

    /** On: the wallpaper, lock glyph and clock of a screen drawn over the keyguard. */
    var lockScreen by mutableStateOf(true)

    /** Seconds since the ring started, driving every overtime counter and the recording elapsed time. */
    var secondsSinceRing by mutableIntStateOf(45)

    fun tick() {
        secondsSinceRing++
    }

    fun cycleRinging(limit: Int) {
        setRinging(if (showing.size >= limit) 1 else showing.size + 1)
    }

    private fun setRinging(count: Int) {
        showing.clear()
        showing.addAll(seed.take(count))
    }

    fun stop(id: Int) {
        showing.removeAll { it.id == id }
    }

    /** ADR 0010: +1 min takes that timer out of the showing exactly like Stop. */
    fun addMinute(id: Int) {
        stop(id)
    }

    fun stopAll() {
        showing.clear()
    }

    /** Restarts the demo after the showing has ended; a real screen would simply have closed. */
    fun reset() {
        setRinging(seed.size)
    }
}

/** The wording of the camera state, in one place so the three variants cannot drift apart. */
internal fun recordingStatusText(mode: EvidenceMode, seconds: Int): String = when (mode) {
    EvidenceMode.Off -> "Not recording · Timer evidence off"
    EvidenceMode.On -> "Recording ${formatCountdown(seconds)}"
    EvidenceMode.CameraFailed -> "Not recording · camera unavailable"
    EvidenceMode.BeforeUnlock -> "Not recording until first unlock"
}

/**
 * When the chip is on, one status surface for the whole showing: a dot and the elapsed recording time, never
 * colour alone. The dot is the only thing that says "recording"; the text always says it in words.
 */
@Composable
internal fun RecordingChip(mode: EvidenceMode, seconds: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.background(
            RingingPalette.background.copy(alpha = 0.72f),
            CircleShape,
        ).padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mode == EvidenceMode.On) {
            Box(Modifier.size(9.dp).background(RingingPalette.recording, CircleShape))
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = recordingStatusText(mode, seconds),
            style = MaterialTheme.typography.labelMedium,
            color = RingingPalette.onBackground,
        )
    }
}

/** A gray box with a camera glyph: the front-camera preview, without any CameraX behind it. */
@Composable
internal fun PreviewPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF343A55)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Videocam,
            contentDescription = "Front camera preview placeholder",
            tint = RingingPalette.onBackgroundMuted,
            modifier = Modifier.size(30.dp),
        )
    }
}

/**
 * The "device is locked" stand-in: a wallpaper, a small lock glyph and the time. It is deliberately the whole
 * visual difference - a real `TimerRingingActivity` uses showWhenLocked and draws over the keyguard itself
 * (AGENTS §7) - and it never offers an unlock action.
 */
@Composable
internal fun RingingWallpaper(locked: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val wallpaper = if (locked) {
        Brush.verticalGradient(listOf(Color(0xFF161C40), Color(0xFF2B3363), Color(0xFF0B0D28)))
    } else {
        Brush.verticalGradient(listOf(RingingPalette.background, RingingPalette.background))
    }
    Column(modifier.fillMaxSize().background(wallpaper).safeDrawingPadding()) {
        if (locked) LockedHeader()
        content()
    }
}

@Composable
private fun LockedHeader() {
    // Sampled once: a wallpaper clock in a prototype has no reason to redraw every second.
    val time = remember { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Device locked",
            tint = RingingPalette.onBackgroundFaint,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = time, style = MaterialTheme.typography.labelMedium, color = RingingPalette.onBackgroundFaint)
    }
}

/**
 * What is left when the showing ends: nothing is ringing, so the activity finishes and the screen closes. A
 * prototype cannot close itself, so it offers the way back to the demo instead.
 */
@Composable
internal fun ShowingEndedPlaceholder(onReset: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Showing ended — screen would close", style = MaterialTheme.typography.titleLarge, color = RingingPalette.onBackground)
        Text(
            text = "Nothing is ringing, and the recording stopped when the last recording-on timer left the showing.",
            style = MaterialTheme.typography.bodySmall,
            color = RingingPalette.onBackgroundFaint,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(
            onClick = onReset,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, RingingPalette.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RingingPalette.onBackgroundMuted),
        ) {
            Text(text = "Reset")
        }
    }
}

// endregion

// region Variant A - the alarm screen's skeleton, one row per ringing timer

/**
 * The alarm twin: "Time's up" where `RingingScreen` puts the clock, one row per ringing timer, and Stop all
 * pinned below them. It is also the screen the heads-up prototype opens inline, which is why it is internal
 * and takes an optional back affordance.
 */
@Composable
internal fun TimerRingingTwin(lab: RingingLab, onBack: (() -> Unit)? = null) {
    RingingWallpaper(locked = lab.lockScreen) {
        Box(Modifier.fillMaxSize()) {
            // The same subtle glow behind the top of the screen as RingingScreen, for the same reason.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-160).dp)
                    .size(520.dp)
                    .background(Brush.radialGradient(listOf(RingingPalette.accent.copy(alpha = 0.18f), Color.Transparent))),
            )
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                RecordingChip(mode = lab.evidence, seconds = lab.secondsSinceRing)
                // The rows scroll; Stop all is pinned outside them so it can never be pushed off screen.
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = "Time's up", style = MaterialTheme.typography.displaySmall, color = RingingPalette.onBackground)
                    Spacer(Modifier.height(18.dp))
                    if (lab.showing.isEmpty()) {
                        ShowingEndedPlaceholder(onReset = { lab.reset() })
                    } else {
                        Text(
                            text = if (lab.showing.size == 1) "1 timer is ringing" else "${lab.showing.size} timers are ringing",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RingingPalette.onBackgroundFaint,
                        )
                        Spacer(Modifier.height(18.dp))
                        lab.showing.forEach { timer -> TwinTimerRow(timer = timer, lab = lab) }
                        Spacer(Modifier.height(16.dp))
                        PreviewPlaceholder(Modifier.size(width = 148.dp, height = 100.dp))
                    }
                }
                if (lab.showing.size >= 2) {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { lab.stopAll() },
                        modifier = Modifier.fillMaxWidth().height(68.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RingingPalette.accent,
                            contentColor = RingingPalette.onBackground,
                        ),
                    ) {
                        Text(text = "Stop all", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                    Text(text = "‹", style = MaterialTheme.typography.headlineSmall, color = RingingPalette.onBackgroundMuted)
                }
            }
        }
    }
}

@Composable
private fun TwinTimerRow(timer: PrototypeRingingTimer, lab: RingingLab) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = RingingPalette.surface,
        contentColor = RingingPalette.onBackground,
        border = BorderStroke(1.dp, RingingPalette.outline),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = timer.title(), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${formatCountdown(timer.totalSeconds)} timer",
                        style = MaterialTheme.typography.bodySmall,
                        color = RingingPalette.onBackgroundFaint,
                    )
                    Text(
                        text = formatOvertime(lab.secondsSinceRing),
                        style = MaterialTheme.typography.titleMedium,
                        color = RingingPalette.recording,
                    )
                }
            }
            TextButton(onClick = { lab.stop(timer.id) }) {
                Text(text = "Stop", color = RingingPalette.onBackgroundMuted)
            }
            TextButton(onClick = { lab.addMinute(timer.id) }) {
                Text(text = "+1 min", color = RingingPalette.onBackgroundMuted)
            }
        }
    }
}

// endregion

// region Variant B - one full card per timer, the list scrolls under a sticky Stop all

@Composable
private fun TimerRingingStacked(lab: RingingLab) {
    RingingWallpaper(locked = lab.lockScreen) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            RecordingBanner(mode = lab.evidence, seconds = lab.secondsSinceRing)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (lab.showing.isEmpty()) {
                    item { ShowingEndedPlaceholder(onReset = { lab.reset() }, modifier = Modifier.fillMaxWidth()) }
                }
                items(lab.showing, key = { it.id }) { timer -> StackedTimerCard(timer = timer, lab = lab) }
            }
            if (lab.showing.size >= 2) {
                // Sticky: outside the scroll, so a long list never hides the one action that stops everything.
                Button(
                    onClick = { lab.stopAll() },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RingingPalette.accent,
                        contentColor = RingingPalette.onBackground,
                    ),
                ) {
                    Text(text = "Stop all", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun RecordingBanner(mode: EvidenceMode, seconds: Int) {
    val recording = mode == EvidenceMode.On
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
        color = if (recording) RingingPalette.recording.copy(alpha = 0.18f) else RingingPalette.surface,
        contentColor = RingingPalette.onBackground,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (recording) Box(Modifier.size(9.dp).background(RingingPalette.recording, CircleShape))
            Text(
                text = recordingStatusText(mode, seconds),
                style = MaterialTheme.typography.labelLarge,
                color = if (recording) RingingPalette.onBackground else RingingPalette.onBackgroundFaint,
            )
        }
    }
}

@Composable
private fun StackedTimerCard(timer: PrototypeRingingTimer, lab: RingingLab) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = RingingPalette.surface,
        contentColor = RingingPalette.onBackground,
        border = BorderStroke(1.dp, RingingPalette.outline),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = timer.title(), style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${formatCountdown(timer.totalSeconds)} timer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RingingPalette.onBackgroundFaint,
                )
                Text(
                    text = formatOvertime(lab.secondsSinceRing),
                    style = MaterialTheme.typography.titleLarge,
                    color = RingingPalette.recording,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { lab.stop(timer.id) },
                    modifier = Modifier.weight(1f).height(72.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RingingPalette.accent,
                        contentColor = RingingPalette.onBackground,
                    ),
                ) {
                    Text(text = "Stop", style = MaterialTheme.typography.titleLarge)
                }
                OutlinedButton(
                    onClick = { lab.addMinute(timer.id) },
                    modifier = Modifier.weight(1f).height(72.dp),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.5.dp, RingingPalette.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RingingPalette.onBackgroundMuted),
                ) {
                    Text(text = "+1 min", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

// endregion

// region Variant C - one focused timer, the rest queued as chips

@Composable
private fun TimerRingingFocus(lab: RingingLab) {
    var focusedId by rememberSaveable { mutableIntStateOf(0) }
    var previewOn by rememberSaveable { mutableStateOf(false) }
    // The one that just ended is the one to show; tapping a chip moves the focus, which is the point here.
    val focused = lab.showing.firstOrNull { it.id == focusedId } ?: lab.showing.lastOrNull()
    RingingWallpaper(locked = lab.lockScreen) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                if (focused == null) {
                    ShowingEndedPlaceholder(onReset = { lab.reset() })
                } else {
                    Text(text = focused.title(), style = MaterialTheme.typography.titleLarge, color = RingingPalette.onBackgroundMuted)
                    Text(
                        text = "${formatCountdown(focused.totalSeconds)} timer",
                        style = MaterialTheme.typography.bodySmall,
                        color = RingingPalette.onBackgroundFaint,
                    )
                    Text(
                        text = formatOvertime(lab.secondsSinceRing),
                        style = MaterialTheme.typography.displayLarge,
                        color = RingingPalette.recording,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { lab.stop(focused.id) },
                            modifier = Modifier.weight(1f).height(78.dp),
                            shape = MaterialTheme.shapes.large,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RingingPalette.accent,
                                contentColor = RingingPalette.onBackground,
                            ),
                        ) {
                            Text(text = "Stop", style = MaterialTheme.typography.headlineSmall)
                        }
                        OutlinedButton(
                            onClick = { lab.addMinute(focused.id) },
                            modifier = Modifier.weight(1f).height(78.dp),
                            shape = MaterialTheme.shapes.large,
                            border = BorderStroke(1.5.dp, RingingPalette.outline),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RingingPalette.onBackgroundMuted),
                        ) {
                            Text(text = "+1 min", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                // The queue: every other ringing timer, one tap away from the centre.
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    lab.showing.filter { it.id != focused?.id }.forEach { timer ->
                        FocusChip(text = "${timer.title()} · ${formatOvertime(lab.secondsSinceRing)}") { focusedId = timer.id }
                    }
                }
                if (lab.showing.size >= 2) {
                    TextButton(onClick = { lab.stopAll() }, modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Stop all", style = MaterialTheme.typography.titleMedium, color = RingingPalette.onBackgroundMuted)
                    }
                }
            }
            RecordingBadge(
                mode = lab.evidence,
                seconds = lab.secondsSinceRing,
                previewOn = previewOn,
                onTogglePreview = { previewOn = !previewOn },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
            if (previewOn && lab.evidence == EvidenceMode.On) {
                PreviewPlaceholder(
                    Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 8.dp).size(width = 88.dp, height = 118.dp),
                )
            }
        }
    }
}

@Composable
private fun FocusChip(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick),
        shape = CircleShape,
        color = RingingPalette.surface,
        contentColor = RingingPalette.onBackgroundMuted,
        border = BorderStroke(1.dp, RingingPalette.outline),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** The corner badge, and the only control that shows or hides the preview next to it. */
@Composable
private fun RecordingBadge(
    mode: EvidenceMode,
    seconds: Int,
    previewOn: Boolean,
    onTogglePreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recording = mode == EvidenceMode.On
    Surface(
        modifier = modifier.clip(CircleShape).clickable(onClick = onTogglePreview),
        shape = CircleShape,
        color = RingingPalette.background.copy(alpha = 0.72f),
        contentColor = RingingPalette.onBackground,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (recording) Box(Modifier.size(8.dp).background(RingingPalette.recording, CircleShape))
            Text(text = recordingStatusText(mode, seconds), style = MaterialTheme.typography.labelSmall)
            if (recording) {
                Text(
                    text = if (previewOn) "· hide preview" else "· show preview",
                    style = MaterialTheme.typography.labelSmall,
                    color = RingingPalette.onBackgroundFaint,
                )
            }
        }
    }
}

// endregion
