// PROTOTYPE for #14, throwaway: the heads-up notification a ringing timer posts while the phone is in use,
// drawn as a Compose card on a fake home screen (no notification is ever posted). No ViewModels, no Room, no
// DataStore, no Hilt: the same in-memory ringing list as the ringing-screen prototype.
package dev.upyet.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.upyet.core.ui.theme.RingingPalette
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * #14, "What does a ringing timer look like?" - the heads-up half.
 *
 * A ringing timer surfaces through a high-importance notification with a full-screen intent, and Android
 * shows a heads-up instead when the phone is in active use. The question here is what that heads-up may
 * offer: an action that ends the ring (A), or nothing but "tap to open" whenever a recording-on timer is
 * ringing (B), because the ringing screen owns the stop and the recording must not be routed around
 * (AGENTS §7). Tapping the body opens the ringing screen either way.
 */
@Composable
internal fun TimerHeadsUpPrototype() {
    // A heads-up only appears while the device is in use, so the ringing screen it opens is not over a
    // keyguard: no wallpaper, no lock glyph.
    val lab = remember { RingingLab().apply { lockScreen = false } }
    LaunchedEffect(lab) {
        while (true) {
            delay(1_000)
            lab.tick()
        }
    }
    var anyRecordingOn by rememberSaveable { mutableStateOf(true) }
    // The chip stands in for the per-timer Timer evidence flag the notification has to read before it draws
    // its actions. Flipping it never changes a timer that already exists (ADR 0010).
    LaunchedEffect(anyRecordingOn) {
        lab.evidence = if (anyRecordingOn) EvidenceMode.On else EvidenceMode.Off
    }
    val variants =
        listOf(
            PrototypeVariant("A", "Actions always") { HeadsUpVariant(lab = lab, alwaysActions = true) },
            PrototypeVariant("B", "Actions only when nothing records") { HeadsUpVariant(lab = lab, alwaysActions = false) },
        )
    PrototypeVariants(variants = variants) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PrototypeCycleChip(label = "Ringing", value = lab.showing.size.toString()) { lab.cycleRinging(HEADS_UP_RINGING_LIMIT) }
            PrototypeControlChip(
                label = "Any recording-on",
                checked = anyRecordingOn,
                onCheckedChange = { anyRecordingOn = it },
            )
        }
    }
}

/** The most ringing timers the heads-up variants show at once; two is where "Stop all" appears. */
private const val HEADS_UP_RINGING_LIMIT = 2

@Composable
private fun HeadsUpVariant(lab: RingingLab, alwaysActions: Boolean) {
    var opened by rememberSaveable { mutableStateOf(false) }
    if (opened) {
        // Tapping the body opens the ringing screen. It is the A variant, inline, with its own way back.
        BackHandler { opened = false }
        TimerRingingTwin(lab = lab, onBack = { opened = false })
        return
    }
    // B: while anything records, no action may end the ring from here. A camera that failed or a device
    // that is not unlocked yet does not change that - the timer is still recording-on.
    val hideActions = !alwaysActions && lab.evidence != EvidenceMode.Off
    FakeHome {
        if (lab.showing.isEmpty()) {
            NotificationGoneNotice()
        } else {
            HeadsUpCard(lab = lab, hideActions = hideActions, onOpen = { opened = true })
        }
        HomeScreenBehind()
    }
}

/** The home screen the notification floats over: wallpaper, clock, a grid of grey squares for app icons. */
@Composable
private fun FakeHome(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A2050), Color(0xFF2E3766), Color(0xFF10132C))))) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            content = content,
        )
    }
}

@Composable
private fun HomeScreenBehind() {
    // Sampled once: a fake wallpaper clock has no reason to redraw every second.
    val time = remember { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = time, style = MaterialTheme.typography.displayLarge, color = Color.White.copy(alpha = 0.92f))
            Text(
                text = "Home screen behind the heads-up",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(4) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.12f)))
                    }
                }
            }
        }
    }
}

/**
 * The heads-up itself: title, what is over by how much, and either the actions A allows or B's single
 * "tap to open". The whole card is the way into the ringing screen, which is why the actions are children
 * rather than a separate bar.
 */
@Composable
private fun HeadsUpCard(lab: RingingLab, hideActions: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val single = lab.showing.singleOrNull()
    val title = when {
        single == null -> "${lab.showing.size} timers ringing"
        single.label.isBlank() -> "Timer"
        else -> "Timer · ${single.label}"
    }
    val body = if (single != null) {
        "${formatCountdown(single.totalSeconds)} timer · ${formatOvertime(lab.secondsSinceRing)}"
    } else {
        "${formatOvertime(lab.secondsSinceRing)} over · one screen for all of them"
    }
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF2C3154),
        contentColor = RingingPalette.onBackground,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(RingingPalette.accent.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        tint = RingingPalette.onBackground,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = RingingPalette.onBackgroundMuted,
                    )
                }
                if (lab.evidence == EvidenceMode.On) {
                    PrototypeTag(text = "REC", color = RingingPalette.recording)
                }
            }
            when {
                hideActions -> Text(
                    text = "Tap to open",
                    style = MaterialTheme.typography.labelMedium,
                    color = RingingPalette.onBackgroundFaint,
                )

                single != null -> Row {
                    TextButton(onClick = { lab.stop(single.id) }) {
                        Text(text = "Stop", color = RingingPalette.onBackground)
                    }
                    // ADR 0010: +1 min takes that timer out of the showing exactly like Stop.
                    TextButton(onClick = { lab.addMinute(single.id) }) {
                        Text(text = "+1 min", color = RingingPalette.onBackground)
                    }
                }

                else -> TextButton(onClick = { lab.stopAll() }) {
                    Text(text = "Stop all", color = RingingPalette.onBackground)
                }
            }
        }
    }
}

@Composable
private fun NotificationGoneNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = RingingPalette.surface,
        contentColor = RingingPalette.onBackground,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "No timers ringing", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "The last timer stopped, so the heads-up would be gone and the ringing screen would have closed.",
                style = MaterialTheme.typography.bodySmall,
                color = RingingPalette.onBackgroundMuted,
            )
        }
    }
}
