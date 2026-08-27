package dev.upyet.alarm.ringing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.upyet.R
import dev.upyet.core.ui.components.ClockText
import dev.upyet.core.ui.theme.ClockLarge
import dev.upyet.core.ui.theme.RingingPalette
import java.time.format.DateTimeFormatter

@Composable
fun RingingScreen(state: RingingUiState, onDismiss: () -> Unit, onSnooze: () -> Unit, evidenceContent: @Composable () -> Unit = {}) {
    val locale = LocalConfiguration.current.locales[0]
    // RingingUiState only carries a clock time, not a date - the ticking view model deliberately drops it
    // since nothing else on this screen needs it. Today's date is a display-only detail read here instead.
    val dateFormatter = DateTimeFormatter.ofPattern(stringResource(R.string.ringing_date_pattern), locale)
    val snoozeLabel = if (state.isLastSnooze) {
        stringResource(R.string.snooze_last)
    } else {
        pluralStringResource(R.plurals.snooze_alarm, state.snoozeMinutes, state.snoozeMinutes)
    }
    val dismissDescription = stringResource(R.string.dismiss_alarm)
    val dismissLabel = stringResource(R.string.dismiss_alarm_action)
    val privacyNote = stringResource(R.string.evidence_privacy_note)

    Box(Modifier.fillMaxSize().background(RingingPalette.background)) {
        // A subtle glow behind the top of the screen, echoing the design without becoming a loud wash.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-160).dp)
                .size(520.dp)
                .background(Brush.radialGradient(listOf(RingingPalette.accent.copy(alpha = 0.18f), Color.Transparent))),
        )
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Snooze and Dismiss are pinned outside this weighted, scrollable area: whatever the evidence UI
            // does - preview, error text, a long label - it can never push the alarm controls off screen.
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    dateFormatter.format(state.currentDate).uppercase(locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = RingingPalette.onBackgroundFaint,
                )
                ClockText(
                    time = state.currentTime,
                    style = ClockLarge,
                    color = RingingPalette.onBackground,
                    meridiemColor = RingingPalette.onBackgroundMuted,
                )
                state.label?.takeIf { it.isNotBlank() }?.let { label ->
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Alarm,
                            contentDescription = null,
                            tint = RingingPalette.accent,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.titleLarge, color = RingingPalette.onBackgroundMuted)
                    }
                }
                Spacer(Modifier.height(30.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(RingingPalette.surface)
                        .border(1.dp, RingingPalette.outline, MaterialTheme.shapes.extraLarge),
                    contentAlignment = Alignment.TopStart,
                ) {
                    evidenceContent()
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = RingingPalette.onBackgroundFaint,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(privacyNote, style = MaterialTheme.typography.bodySmall, color = RingingPalette.onBackgroundFaint)
                }
                state.ringingNoteRes?.let { noteRes ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(noteRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = RingingPalette.onBackgroundFaint,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (state.snoozeAllowed) {
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier.fillMaxWidth().height(62.dp).semantics {
                        contentDescription = snoozeLabel
                    },
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.5.dp, RingingPalette.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RingingPalette.onBackgroundMuted),
                    content = { Text(snoozeLabel, style = MaterialTheme.typography.titleLarge) },
                )
            } else {
                Text(
                    stringResource(R.string.ringing_last_alarm),
                    style = MaterialTheme.typography.bodySmall,
                    color = RingingPalette.onBackgroundFaint,
                )
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(68.dp).semantics {
                    contentDescription = dismissDescription
                },
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = RingingPalette.accent, contentColor = RingingPalette.onBackground),
                content = { Text(dismissLabel, style = MaterialTheme.typography.headlineSmall) },
            )
        }
    }
}
