package dev.upyet.alarm.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.upyet.R
import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.NextAlarmInfo
import dev.upyet.alarm.domain.Recurrence
import dev.upyet.core.ui.PermissionOnboardingCard
import dev.upyet.core.ui.components.ClockText
import dev.upyet.core.ui.components.PrimaryButton
import dev.upyet.core.ui.components.SectionLabel
import dev.upyet.core.ui.components.StatusBadge
import dev.upyet.core.ui.components.UpYetCard
import dev.upyet.core.ui.currentLocale
import dev.upyet.core.ui.rememberLocalized
import dev.upyet.core.ui.theme.Ink
import dev.upyet.core.ui.theme.MinTouchTarget
import dev.upyet.core.ui.theme.RingingPalette
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

@Composable
fun AlarmListScreen(
    onEdit: (Long) -> Unit,
    onReliability: () -> Unit,
    viewModel: AlarmListViewModel = hiltViewModel(),
    onSkipNext: (Alarm) -> Unit = { viewModel.toggleSkipNext(it) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AlarmListContent(
        state,
        onEdit,
        onReliability,
        { alarm, enabled -> viewModel.setEnabled(alarm, enabled) },
        { viewModel.delete(it) },
        onSkipNext,
        TurnOffPromptActions(
            onSkip = viewModel::skipInsteadOfTurnOff,
            onTurnOff = viewModel::confirmTurnOff,
            onDismiss = viewModel::dismissTurnOffPrompt,
        ),
    )
}

/** The three answers to [TurnOffPrompt], bundled so the content composable stays readable. */
data class TurnOffPromptActions(val onSkip: () -> Unit = {}, val onTurnOff: () -> Unit = {}, val onDismiss: () -> Unit = {})

@Composable
fun AlarmListContent(
    state: AlarmListUiState,
    onEdit: (Long) -> Unit,
    onReliability: () -> Unit,
    onEnabled: (Alarm, Boolean) -> Unit = { _, _ -> },
    onDelete: (AlarmId) -> Unit = {},
    onSkipNext: (Alarm) -> Unit = {},
    turnOffActions: TurnOffPromptActions = TurnOffPromptActions(),
) {
    state.turnOffPrompt?.let { prompt -> TurnOffDialog(prompt, turnOffActions) }
    val isEmpty = state.alarms.isEmpty()
    Scaffold(
        floatingActionButton = {
            if (!isEmpty) {
                ExtendedFloatingActionButton(
                    onClick = { onEdit(-1) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_alarm)) },
                )
            }
        },
        bottomBar = {
            if (isEmpty) {
                Box(Modifier.fillMaxWidth().padding(20.dp)) {
                    PrimaryButton(
                        text = stringResource(R.string.create_first_alarm),
                        onClick = { onEdit(-1) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Add,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            state.errorRes?.let { error -> item { SchedulingBanner(error, state.showReliabilityAction, onReliability) } }
            item { PermissionOnboardingCard() }
            if (isEmpty) {
                item { EmptyAlarms() }
            } else {
                state.nextAlarm?.let { next -> item { NextAlarmHero(next, state.now) } }
                item { AlarmsSectionHeader(state.alarms) }
                items(state.alarms, key = { it.id.value }) { alarm ->
                    AlarmRow(
                        alarm,
                        state.activeSkips[alarm.id],
                        { onEnabled(alarm, it) },
                        { onEdit(alarm.id.value) },
                        { onDelete(alarm.id) },
                        { onSkipNext(alarm) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmsSectionHeader(alarms: List<Alarm>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(stringResource(R.string.your_alarms))
        Text(
            stringResource(R.string.alarms_enabled_count, alarms.count { it.enabled }, alarms.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The dark hero card: the soonest enabled alarm and a countdown that the ViewModel's ticker keeps fresh. */
@Composable
private fun NextAlarmHero(info: NextAlarmInfo, now: Instant, modifier: Modifier = Modifier) {
    val zone = remember { ZoneId.systemDefault() }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = Ink) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.next_alarm).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = RingingPalette.onBackgroundFaint,
            )
            ClockText(
                time = info.time,
                style = MaterialTheme.typography.displayMedium,
                color = RingingPalette.onBackground,
                meridiemColor = RingingPalette.onBackgroundFaint,
            )
            Text(
                text = stringResource(R.string.next_alarm_when, dayWord(info.firesAt, zone, now), ringsInWord(info.firesAt, now)),
                style = MaterialTheme.typography.bodyMedium,
                color = RingingPalette.onBackgroundMuted,
            )
            if (info.evidenceEnabled) {
                StatusBadge(
                    text = stringResource(R.string.evidence_on),
                    container = Color.White.copy(alpha = 0.10f),
                    content = Color.White,
                    icon = Icons.Default.Videocam,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun dayWord(firesAt: Instant, zone: ZoneId, now: Instant): String {
    val isToday = firesAt.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()
    return stringResource(if (isToday) R.string.next_alarm_today else R.string.next_alarm_tomorrow)
}

@Composable
private fun ringsInWord(firesAt: Instant, now: Instant): String {
    val minutesUntil = Duration.between(now, firesAt).toMinutes()
    return when {
        minutesUntil <= 0 -> stringResource(R.string.rings_now)
        minutesUntil < 60 -> pluralStringResource(R.plurals.rings_in_minutes, minutesUntil.toInt(), minutesUntil.toInt())
        else -> stringResource(R.string.rings_in_hours_minutes, (minutesUntil / 60).toInt(), (minutesUntil % 60).toInt())
    }
}

@Composable
private fun EmptyAlarms(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(top = 24.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.size(140.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.no_alarms),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = stringResource(R.string.no_alarms_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SchedulingBanner(@StringRes message: Int, showReliability: Boolean, onReliability: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Error,
                contentDescription = stringResource(R.string.warning),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Text(
                stringResource(message),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (showReliability) {
                TextButton(onClick = onReliability) {
                    Text(stringResource(R.string.open_reliability), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    activeSkipDate: LocalDate?,
    onEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSkipNext: () -> Unit = {},
) {
    var showDelete by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val onSurface = if (alarm.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    UpYetCard(
        modifier = Modifier.clickable(onClick = onEdit).alpha(if (alarm.enabled) 1f else 0.72f),
        contentPadding = PaddingValues(16.dp),
    ) {
        // The switch and the overflow sit beside the time rather than in a trailing column: given a
        // column of their own they stole the width the seven weekday pills need, and the pills clipped.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            ClockText(
                time = alarm.time,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.displaySmall,
                color = onSurface,
                meridiemColor = onSurfaceVariant,
            )
            val enabledDescription = stringResource(R.string.alarm_enabled_description)
            // The switch keeps its full size: at the time's display size the two now stand level, and
            // scaling it down only cost it a touch target it is entitled to.
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onEnabled,
                modifier = Modifier.semantics { contentDescription = enabledDescription },
            )
            Box {
                // The overflow's 48 dp touch target centres a 24 dp glyph, which reads as a wide gap
                // next to the switch. The negative end offset pulls the glyph back towards it without
                // taking anything off the target itself.
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.offset(x = OVERFLOW_NUDGE).sizeIn(minWidth = MinTouchTarget, minHeight = MinTouchTarget),
                ) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.more_options), tint = onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (alarm.recurrence != Recurrence.OneTime && alarm.enabled) {
                        val isSkipActive = activeSkipDate != null
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (isSkipActive) R.string.cancel_skip_next_alarm else R.string.skip_next_alarm,
                                    ),
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.SkipNext, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onSkipNext()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_alarm)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            showDelete = true
                        },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                alarm.label.ifBlank { stringResource(R.string.unnamed_alarm) },
                style = MaterialTheme.typography.titleSmall,
                color = onSurface,
            )
            if (!alarm.soundEnabled) {
                Icon(
                    imageVector = if (alarm.vibrationEnabled) Icons.Filled.Vibration else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = stringResource(
                        if (alarm.vibrationEnabled) R.string.alarm_vibrate_only else R.string.alarm_silent,
                    ),
                    tint = onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp).size(16.dp),
                )
            }
        }
        activeSkipDate?.let { skipDate ->
            StatusBadge(
                text = stringResource(R.string.alarm_skipping_next, skipDateText(skipDate)),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                icon = Icons.Default.SkipNext,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (alarm.recurrence == Recurrence.OneTime) {
            Text(
                stringResource(R.string.alarm_never_repeats),
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant,
                modifier = Modifier.padding(top = 9.dp),
            )
        } else {
            RecurrencePills(alarm.recurrence, alarm.enabled, Modifier.padding(top = 9.dp))
        }
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.delete_alarm_title)) },
            text = { Text(stringResource(R.string.delete_alarm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    onDelete()
                }) {
                    Icon(Icons.Default.Delete, stringResource(R.string.delete))
                    Text(stringResource(R.string.confirm_delete))
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * Seven day pills, Monday first. Daily lights every pill; a weekly recurrence lights only its days.
 *
 * The pills size themselves to the width they are given, capped at [MAX_DAY_PILL], so all seven are
 * always visible - on a narrow screen or a large display scale they shrink rather than clip.
 */
@Composable
private fun RecurrencePills(recurrence: Recurrence, alarmEnabled: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val gap = 5.dp
        val available = (maxWidth - gap * (DayOfWeek.entries.size - 1)) / DayOfWeek.entries.size
        val pill = minOf(available, MAX_DAY_PILL)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            DayOfWeek.entries.forEach { day ->
                val lit = when (recurrence) {
                    Recurrence.Daily -> true
                    is Recurrence.Weekly -> day in recurrence.days
                    Recurrence.OneTime -> false
                }
                DayPill(day, lit && alarmEnabled, pill)
            }
        }
    }
}

@Composable
private fun DayPill(day: DayOfWeek, lit: Boolean, size: androidx.compose.ui.unit.Dp) {
    val container = if (lit) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = CircleShape, color = container, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                day.getDisplayName(TextStyle.NARROW, currentLocale()),
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 1,
            )
        }
    }
}

/**
 * Asks whether turning a recurring alarm off meant "skip the next one". The buttons stack because the skip
 * label carries a date and would not fit beside the others on a narrow screen; skip comes first as the
 * reversible answer. Dismissing changes nothing, so the switch simply stays on.
 */
@Composable
private fun TurnOffDialog(prompt: TurnOffPrompt, actions: TurnOffPromptActions) {
    val timeFormatter = rememberLocalized { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(it) }
    val skipDate = skipDateText(prompt.skipDate)
    AlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text(stringResource(R.string.turn_off_prompt_title, timeFormatter.format(prompt.time))) },
        text = { Text(stringResource(R.string.turn_off_prompt_message)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = actions.onSkip) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.turn_off_prompt_skip, skipDate))
                }
                TextButton(onClick = actions.onTurnOff) { Text(stringResource(R.string.turn_off_prompt_confirm)) }
                TextButton(onClick = actions.onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** One format for a skipped date wherever it appears, so the prompt and the row badge name the same day the same way. */
@Composable
private fun skipDateText(date: LocalDate): String =
    rememberLocalized { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(it) }.format(date)

/** Pulls the overflow glyph back towards the switch; its touch target keeps its full width. */
private val OVERFLOW_NUDGE = 6.dp
private val MAX_DAY_PILL = 26.dp
