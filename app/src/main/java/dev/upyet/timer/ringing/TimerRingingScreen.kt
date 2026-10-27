package dev.upyet.timer.ringing

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.upyet.R
import dev.upyet.core.ui.theme.ClockLarge
import dev.upyet.core.ui.theme.RingingPalette
import dev.upyet.evidence.camera.EvidenceRecordingState
import dev.upyet.evidence.domain.EvidenceErrorCode
import dev.upyet.evidence.ui.RecordingIndicator
import dev.upyet.timer.domain.TimerId
import java.time.Duration

/**
 * The timer ringing screen, issue #14 variant C "focus + queue": one timer in the centre with huge Stop and +1 min,
 * the other ringing timers as chips that move the focus, and Stop all when two or more ring. The controls sit
 * outside the scrollable area, so neither the preview nor a long label can push them off screen, and nothing here
 * depends on the camera state (AGENTS.md §9a). Over the keyguard it shows the lock glyph and never an unlock action.
 */
@Composable
fun TimerRingingScreen(
    state: TimerRingingUiState,
    locked: Boolean,
    evidenceState: EvidenceRecordingState,
    previewVisible: Boolean,
    onTogglePreview: () -> Unit,
    onFocus: (TimerId) -> Unit,
    onStop: (TimerId) -> Unit,
    onAddMinute: (TimerId) -> Unit,
    onStopAll: () -> Unit,
    preview: @Composable (visible: Boolean) -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(RingingPalette.background)) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-160).dp)
                .size(520.dp)
                .background(Brush.radialGradient(listOf(RingingPalette.accent.copy(alpha = 0.18f), Color.Transparent))),
        )
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            TopRow(state.recordingOn, locked, evidenceState, previewVisible, onTogglePreview)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Composed whenever a timer records, visible or not: the preview is also the camera's surface, and
                // recording gets no frames without it (EvidencePreview).
                if (state.recordingOn) {
                    preview(previewVisible)
                    if (previewVisible) Spacer(Modifier.height(20.dp))
                }
                state.focused?.let { FocusedTimer(it) }
            }
            state.focused?.let { focused ->
                Spacer(Modifier.height(16.dp))
                Controls(focused.id, onStop, onAddMinute)
            }
            if (state.queue.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.queue.forEach { timer -> QueueChip(timer, onClick = { onFocus(timer.id) }) }
                }
            }
            if (state.showStopAll) {
                TextButton(onClick = onStopAll, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(
                        stringResource(R.string.timer_ringing_stop_all),
                        style = MaterialTheme.typography.titleMedium,
                        color = RingingPalette.onBackgroundMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopRow(
    recordingOn: Boolean,
    locked: Boolean,
    evidenceState: EvidenceRecordingState,
    previewVisible: Boolean,
    onTogglePreview: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (locked) {
            // The glyph is the whole lock-screen affordance: this screen never offers to unlock (AGENTS.md §7).
            Icon(
                Icons.Default.Lock,
                contentDescription = stringResource(R.string.timer_ringing_locked),
                tint = RingingPalette.onBackgroundFaint,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (recordingOn) {
            RecordingIndicator(evidenceState, unavailableLabel = { timerUnavailableLabel(it) })
            if (evidenceState is EvidenceRecordingState.Recording) {
                IconButton(onClick = onTogglePreview) {
                    Icon(
                        if (previewVisible) Icons.Default.VideocamOff else Icons.Default.Videocam,
                        contentDescription = stringResource(
                            if (previewVisible) R.string.timer_ringing_hide_preview else R.string.timer_ringing_show_preview,
                        ),
                        tint = RingingPalette.onBackgroundMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusedTimer(timer: RingingTimerItem) {
    val overtime = overtimeText(timer.overtime)
    val overtimeDescription = stringResource(R.string.timer_ringing_overtime_description, durationText(timer.overtime))
    Text(
        timer.title(),
        style = MaterialTheme.typography.titleLarge,
        color = RingingPalette.onBackgroundMuted,
        textAlign = TextAlign.Center,
    )
    timer.length?.let { length ->
        Text(
            stringResource(R.string.timer_ringing_length, durationText(length)),
            style = MaterialTheme.typography.bodyMedium,
            color = RingingPalette.onBackgroundFaint,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        overtime,
        style = ClockLarge,
        color = RingingPalette.onBackground,
        modifier = Modifier.semantics { contentDescription = overtimeDescription },
    )
}

@Composable
private fun Controls(id: TimerId, onStop: (TimerId) -> Unit, onAddMinute: (TimerId) -> Unit) {
    val addMinuteDescription = stringResource(R.string.timer_ringing_add_minute_description)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { onAddMinute(id) },
            modifier = Modifier.weight(1f).height(78.dp).semantics { contentDescription = addMinuteDescription },
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.5.dp, RingingPalette.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RingingPalette.onBackgroundMuted),
        ) {
            Text(stringResource(R.string.timer_ringing_add_minute), style = MaterialTheme.typography.headlineSmall)
        }
        Button(
            onClick = { onStop(id) },
            modifier = Modifier.weight(1f).height(78.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = RingingPalette.accent, contentColor = RingingPalette.onBackground),
        ) {
            Text(stringResource(R.string.timer_ringing_stop), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun QueueChip(timer: RingingTimerItem, onClick: () -> Unit) {
    val focusAction = stringResource(R.string.timer_ringing_focus_action)
    Surface(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = focusAction, role = Role.Button, onClick = onClick),
        shape = CircleShape,
        color = RingingPalette.surface,
        contentColor = RingingPalette.onBackgroundMuted,
        border = BorderStroke(1.dp, RingingPalette.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.timer_ringing_chip, timer.title(), overtimeText(timer.overtime)),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun RingingTimerItem.title(): String = label.ifBlank { stringResource(R.string.timer_ringing_title) }

@Composable
private fun durationText(duration: Duration): String =
    clockText(duration, R.string.timer_ringing_duration_minutes, R.string.timer_ringing_duration_hours)

@Composable
private fun overtimeText(overtime: Duration): String =
    clockText(overtime, R.string.timer_ringing_overtime, R.string.timer_ringing_overtime_hours)

@Composable
private fun clockText(duration: Duration, @StringRes minutesFormat: Int, @StringRes hoursFormat: Int): String {
    val parts = ClockParts.of(duration)
    return if (parts.hours > 0) {
        stringResource(hoursFormat, parts.hours, parts.minutes, parts.seconds)
    } else {
        stringResource(minutesFormat, parts.minutes, parts.seconds)
    }
}

/** Issue #14's wording for why a timer showing is not recording; every case still leaves every button working. */
@Composable
private fun timerUnavailableLabel(errorCode: EvidenceErrorCode): String = when (errorCode) {
    EvidenceErrorCode.DIRECT_BOOT_UNAVAILABLE -> stringResource(R.string.timer_evidence_before_unlock)

    EvidenceErrorCode.PERMISSION_DENIED -> stringResource(R.string.timer_evidence_permission_denied)

    EvidenceErrorCode.CAMERA_UNAVAILABLE,
    EvidenceErrorCode.CAMERA_IN_USE,
    EvidenceErrorCode.INITIALIZATION_FAILED,
    EvidenceErrorCode.RECORDING_START_FAILED,
    -> stringResource(R.string.timer_evidence_camera_unavailable)

    else -> stringResource(R.string.timer_evidence_not_recording)
}
