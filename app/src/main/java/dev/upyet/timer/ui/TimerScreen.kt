package dev.upyet.timer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.upyet.R
import dev.upyet.core.ui.components.UpYetCard
import dev.upyet.core.ui.theme.ControlHeight
import dev.upyet.core.ui.theme.MinTouchTarget
import dev.upyet.timer.domain.TimerId

/** The quick lengths under the wheels, in minutes. */
val TIMER_PRESET_MINUTES = listOf(1, 3, 5, 10, 15, 30)

private const val WHEEL_HOURS_MAX = 23
private const val WHEEL_SEXAGESIMAL_MAX = 59
private const val WHEELS_WIDTH_FRACTION = 0.9f

/** Every event the Timer tab can raise; the defaults are no-ops so a test sets only what it checks. */
@Immutable
data class TimerCallbacks(
    val onHours: (Int) -> Unit = {},
    val onMinutes: (Int) -> Unit = {},
    val onSeconds: (Int) -> Unit = {},
    val onPreset: (Int) -> Unit = {},
    val onLabel: (String) -> Unit = {},
    val onRecordNewTimer: (Boolean) -> Unit = {},
    val onStart: () -> Unit = {},
    val onStartTimer: (TimerId) -> Unit = {},
    val onPause: (TimerId) -> Unit = {},
    val onResume: (TimerId) -> Unit = {},
    val onStop: (TimerId) -> Unit = {},
    val onAddMinute: (TimerId) -> Unit = {},
    val onReset: (TimerId) -> Unit = {},
    val onDelete: (TimerId) -> Unit = {},
    val onEvidence: (TimerId, Boolean) -> Unit = { _, _ -> },
)

/** The Timer tab, stateless: it renders [state] and raises [callbacks], nothing else. */
@Composable
fun TimerContent(state: TimerUiState, callbacks: TimerCallbacks, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "wheels") { Wheels(state, callbacks) }
        item(key = "presets") { Presets(callbacks.onPreset) }
        item(key = "new") { NewTimerCard(state, callbacks) }
        if (state.timers.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.timer_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            items(state.timers, key = { it.id.value }) { row -> TimerRow(row, callbacks) }
        }
    }
}

@Composable
private fun Wheels(state: TimerUiState, callbacks: TimerCallbacks) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(WHEELS_WIDTH_FRACTION), verticalAlignment = Alignment.CenterVertically) {
            val hours = stringResource(R.string.timer_wheel_hours)
            val minutes = stringResource(R.string.timer_wheel_minutes)
            val seconds = stringResource(R.string.timer_wheel_seconds)
            WheelWithCaption(state.hours, WHEEL_HOURS_MAX, hours, "timer_wheel_hours", callbacks.onHours, Modifier.weight(1f))
            Separator()
            WheelWithCaption(state.minutes, WHEEL_SEXAGESIMAL_MAX, minutes, "timer_wheel_minutes", callbacks.onMinutes, Modifier.weight(1f))
            Separator()
            WheelWithCaption(state.seconds, WHEEL_SEXAGESIMAL_MAX, seconds, "timer_wheel_seconds", callbacks.onSeconds, Modifier.weight(1f))
        }
    }
}

@Composable
private fun WheelWithCaption(value: Int, max: Int, label: String, tag: String, onChange: (Int) -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TimerWheel(value, 0..max, label, tag, onChange, Modifier.fillMaxWidth())
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Separator() {
    Text(
        text = ":",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // Level with the selected row, not the middle of wheel plus caption.
        modifier = Modifier.padding(horizontal = 2.dp).padding(bottom = 16.dp).clearAndSetSemantics {},
    )
}

@Composable
private fun Presets(onPreset: (Int) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        TIMER_PRESET_MINUTES.forEach { minutes ->
            val description = pluralStringResource(R.plurals.timer_preset_description, minutes, minutes)
            AssistChip(
                onClick = { onPreset(minutes) },
                label = { Text(stringResource(R.string.timer_preset, minutes)) },
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
    }
}

@Composable
private fun NewTimerCard(state: TimerUiState, callbacks: TimerCallbacks) {
    UpYetCard(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
        OutlinedTextField(
            value = state.label,
            onValueChange = callbacks.onLabel,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.timer_label_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .toggleable(value = state.recordNewTimer, role = Role.Switch, onValueChange = callbacks.onRecordNewTimer)
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.timer_record_new),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = state.recordNewTimer, onCheckedChange = null)
        }
        Button(
            onClick = callbacks.onStart,
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth().heightIn(min = ControlHeight).padding(top = 4.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(stringResource(R.string.timer_start), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * One dense row. Which controls appear is decided by [TimerRowState] (`canX`), not here, so the tag
 * and the controls cannot disagree; the tag is always a word, never colour alone.
 */
@Composable
fun TimerRow(row: TimerRowState, callbacks: TimerCallbacks, modifier: Modifier = Modifier) {
    val attention = row.tag == TimerTag.RINGING || row.tag == TimerTag.MISSED
    val remainingColor = when (row.tag) {
        TimerTag.RINGING -> MaterialTheme.colorScheme.error
        TimerTag.IDLE, TimerTag.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    UpYetCard(
        modifier.testTag("timer_row_${row.id.value}"),
        contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.label.ifBlank { stringResource(R.string.timer_untitled) },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(row.tag.labelRes()),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (attention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            if (row.showsEvidenceChip) EvidenceChip(row, callbacks.onEvidence)
            if (row.canReset) {
                RowIconButton(Icons.Default.Replay, R.string.timer_reset) { callbacks.onReset(row.id) }
            }
            if (row.canDelete) {
                RowIconButton(Icons.Default.Delete, R.string.timer_delete) { callbacks.onDelete(row.id) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = formatTimerDuration(row.remaining),
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    color = remainingColor,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = stringResource(R.string.timer_length, formatTimerDuration(row.length)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.canAddMinute) {
                val description = stringResource(R.string.timer_add_minute_description)
                TextButton(
                    onClick = { callbacks.onAddMinute(row.id) },
                    modifier = Modifier.heightIn(min = MinTouchTarget).semantics { contentDescription = description },
                ) { Text(stringResource(R.string.timer_add_minute), style = MaterialTheme.typography.labelLarge) }
            }
            PrimaryAction(row, callbacks)
        }
    }
}

@Composable
private fun PrimaryAction(row: TimerRowState, callbacks: TimerCallbacks) {
    val (icon, description, onClick) = when {
        row.canStop -> Triple(Icons.Default.Stop, R.string.timer_stop) { callbacks.onStop(row.id) }
        row.canPause -> Triple(Icons.Default.Pause, R.string.timer_pause) { callbacks.onPause(row.id) }
        row.canResume -> Triple(Icons.Default.PlayArrow, R.string.timer_resume) { callbacks.onResume(row.id) }
        row.canStart -> Triple(Icons.Default.PlayArrow, R.string.timer_start) { callbacks.onStartTimer(row.id) }
        else -> return
    }
    val modifier = Modifier.size(MinTouchTarget).padding(2.dp)
    if (row.canStop) {
        FilledIconButton(onClick = onClick, modifier = modifier) { Icon(icon, stringResource(description)) }
    } else {
        FilledTonalIconButton(onClick = onClick, modifier = modifier) { Icon(icon, stringResource(description)) }
    }
}

@Composable
private fun EvidenceChip(row: TimerRowState, onEvidence: (TimerId, Boolean) -> Unit) {
    val description = stringResource(if (row.evidenceEnabled) R.string.timer_rec_on_description else R.string.timer_rec_off_description)
    FilterChip(
        selected = row.evidenceEnabled,
        onClick = { onEvidence(row.id, !row.evidenceEnabled) },
        enabled = row.canToggleEvidence,
        label = { Text(stringResource(if (row.evidenceEnabled) R.string.timer_rec_on else R.string.timer_rec_off)) },
        modifier = Modifier.padding(end = 4.dp).semantics { contentDescription = description },
    )
}

@Composable
private fun RowIconButton(icon: ImageVector, description: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(MinTouchTarget)) {
        Icon(icon, stringResource(description), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun TimerTag.labelRes(): Int = when (this) {
    TimerTag.RINGING -> R.string.timer_tag_ringing
    TimerTag.MISSED -> R.string.timer_tag_missed
    TimerTag.RUNNING -> R.string.timer_tag_running
    TimerTag.PAUSED -> R.string.timer_tag_paused
    TimerTag.IDLE -> R.string.timer_tag_idle
}
