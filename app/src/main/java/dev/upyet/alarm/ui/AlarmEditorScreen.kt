package dev.upyet.alarm.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.upyet.R
import dev.upyet.alarm.domain.Recurrence
import dev.upyet.core.ui.components.ClockText
import dev.upyet.core.ui.components.PrimaryButton
import dev.upyet.core.ui.components.RowDivider
import dev.upyet.core.ui.components.SecondaryButton
import dev.upyet.core.ui.components.SectionLabel
import dev.upyet.core.ui.components.SettingsRow
import dev.upyet.core.ui.components.UpYetCard
import dev.upyet.core.ui.components.UpYetTopBar
import dev.upyet.core.ui.currentLocale
import dev.upyet.core.ui.theme.MinTouchTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle

private val SNOOZE_OPTIONS = listOf(5, 9, 10, 15, 30)

/** The ringtone row stays readable but inert while sound is switched off. */
private const val DISABLED_ROW_ALPHA = 0.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    alarmId: Long?,
    onDone: () -> Unit,
    onReliability: () -> Unit = {},
    viewModel: AlarmEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AlarmEditorContent(
        state = state,
        isNewAlarm = alarmId == null || alarmId < 0,
        onUpdate = viewModel::update,
        onSave = { viewModel.save(onDone) },
        onCancel = onDone,
        onReliability = onReliability,
    )
}

/** Stateless editor, so the layout can be exercised without Hilt or a ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorContent(
    state: AlarmEditorUiState,
    isNewAlarm: Boolean,
    onUpdate: ((AlarmEditorUiState) -> AlarmEditorUiState) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onReliability: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            UpYetTopBar(
                title = stringResource(if (isNewAlarm) R.string.create_alarm else R.string.edit_alarm),
                onBack = onCancel,
            )
        },
        // Save and Cancel live in a fixed bottom bar: the form is taller than a phone screen, and a
        // primary action that can only be reached by scrolling is a primary action that gets missed.
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column {
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp).imePadding(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SecondaryButton(text = stringResource(R.string.cancel), onClick = onCancel, modifier = Modifier.weight(1f))
                        PrimaryButton(
                            text = stringResource(R.string.save),
                            onClick = onSave,
                            enabled = state.isLoaded,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (!state.isLoaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        AlarmEditorForm(
            state = state,
            onUpdate = onUpdate,
            onReliability = onReliability,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AlarmEditorForm(
    state: AlarmEditorUiState,
    onUpdate: ((AlarmEditorUiState) -> AlarmEditorUiState) -> Unit,
    onReliability: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val ringtoneLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                onUpdate { it.copy(soundUri = uri?.toString()) }
            }
        }
    // The alarm always has a sound - either the one the user picked, or the system's default alarm
    // sound - so the row can always show a name rather than leaving the user guessing what will play.
    // Resolving that name reads the media store, so it happens off the main thread: the row shows
    // nothing until the title arrives rather than stalling the first frame of the editor.
    val ringtoneName by produceState<String?>(initialValue = null, state.soundUri) {
        value = withContext(Dispatchers.IO) {
            val uri = state.soundUri?.let(Uri::parse) ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            uri?.let { runCatching { RingtoneManager.getRingtone(context, it)?.getTitle(context) }.getOrNull() }
        }
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        state.errorRes?.let { error ->
            Text(stringResource(error), color = MaterialTheme.colorScheme.error)
            if (state.showReliabilityAction) {
                SecondaryButton(text = stringResource(R.string.open_reliability), onClick = onReliability)
            }
        }

        OutlinedTextField(
            value = state.label,
            onValueChange = { value -> onUpdate { it.copy(label = value) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.alarm_label)) },
            leadingIcon = { Icon(Icons.Filled.Label, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )

        UpYetCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 20.dp)) {
            // Follow the device's clock setting, so a 12-hour locale gets the AM/PM selector rather
            // than being forced onto a 24-hour dial.
            val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
            // The picker starts from the alarm's time and then owns it - the form is only composed once the
            // alarm has loaded, so there is no stale default to correct. It is deliberately not rebuilt when
            // state.time changes: a rebuilt picker comes back in its initial hour mode, which is how picking
            // a minute used to throw the dial back to the hour and leave the minute impossible to adjust.
            val timeState = rememberTimePickerState(state.time.hour, state.time.minute, is24Hour)
            LaunchedEffect(timeState.hour, timeState.minute) {
                val picked = LocalTime.of(timeState.hour, timeState.minute)
                if (picked != state.time) onUpdate { it.copy(time = picked) }
            }
            TimePickerCard(timeState)
            // The picker's own boxes are the editable time; this is the read-back of what was chosen,
            // so it is muted rather than carrying the primary colour and competing with them.
            ClockText(
                time = state.time,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionLabel(stringResource(R.string.recurrence))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.recurrence == Recurrence.OneTime,
                onClick = { onUpdate { it.copy(recurrence = Recurrence.OneTime) } },
                label = { Text(stringResource(R.string.repeat_once)) },
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
            )
            FilterChip(
                selected = state.recurrence == Recurrence.Daily,
                onClick = { onUpdate { it.copy(recurrence = Recurrence.Daily) } },
                label = { Text(stringResource(R.string.repeat_daily)) },
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
            )
            FilterChip(
                selected = state.recurrence is Recurrence.Weekly,
                onClick = {
                    // Only switching in from Once/Daily needs a default: an already-Weekly recurrence stays
                    // as it is, and the rule below keeps it from ever landing on an empty set of days.
                    if (state.recurrence !is Recurrence.Weekly) {
                        onUpdate { it.copy(recurrence = Recurrence.Weekly(DayOfWeek.entries.toSet())) }
                    }
                },
                label = { Text(stringResource(R.string.repeat_custom)) },
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
            )
        }
        val selectedDays = (state.recurrence as? Recurrence.Weekly)?.days.orEmpty()
        if (state.recurrence is Recurrence.Weekly) {
            // FlowRow, not Row: seven chips do not fit across a narrow screen and would be clipped.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in selectedDays,
                        onClick = {
                            val days = selectedDays.toMutableSet().apply { if (!remove(day)) add(day) }
                            if (days.isNotEmpty()) onUpdate { it.copy(recurrence = Recurrence.Weekly(days)) }
                        },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, currentLocale())) },
                        modifier = Modifier.heightIn(min = MinTouchTarget),
                    )
                }
            }
        }

        UpYetCard {
            SettingsRow(
                title = stringResource(R.string.sound),
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                trailing = {
                    Switch(checked = state.soundEnabled, onCheckedChange = { value -> onUpdate { it.copy(soundEnabled = value) } })
                },
            )
            RowDivider()
            SettingsRow(
                title = stringResource(R.string.ringtone),
                icon = Icons.Filled.MusicNote,
                value = ringtoneName,
                modifier = Modifier.alpha(if (state.soundEnabled) 1f else DISABLED_ROW_ALPHA),
                showChevron = state.soundEnabled,
                onClick = if (state.soundEnabled) {
                    {
                        val intent =
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, state.soundUri?.let(Uri::parse))
                                // Silence is now the Sound switch's job, and the picker only picks sounds.
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        ringtoneLauncher.launch(intent)
                    }
                } else {
                    null
                },
            )
            RowDivider()
            var snoozeExpanded by remember { mutableStateOf(false) }
            Box {
                SettingsRow(
                    title = stringResource(R.string.snooze_minutes),
                    icon = Icons.Filled.Snooze,
                    value = pluralStringResource(R.plurals.snooze_option, state.snoozeMinutes, state.snoozeMinutes),
                    showChevron = true,
                    onClick = { snoozeExpanded = true },
                )
                DropdownMenu(snoozeExpanded, { snoozeExpanded = false }) {
                    SNOOZE_OPTIONS.forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text(pluralStringResource(R.plurals.snooze_option, minutes, minutes)) },
                            onClick = {
                                onUpdate { it.copy(snoozeMinutes = minutes) }
                                snoozeExpanded = false
                            },
                        )
                    }
                }
            }
            RowDivider()
            SettingsRow(
                title = stringResource(R.string.vibration),
                icon = Icons.Filled.Vibration,
                trailing = { Switch(checked = state.vibration, onCheckedChange = { value -> onUpdate { it.copy(vibration = value) } }) },
            )
            if (!state.soundEnabled && !state.vibration) {
                Text(
                    stringResource(R.string.alarm_screen_only_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp),
                )
            }
            RowDivider()
            SettingsRow(
                title = stringResource(R.string.video_evidence),
                subtitle = stringResource(R.string.evidence_explanation),
                icon = Icons.Filled.Videocam,
                trailing = { Switch(checked = state.evidence, onCheckedChange = { value -> onUpdate { it.copy(evidence = value) } }) },
            )
        }
    }
}

/**
 * Material's time picker is built from fixed sizes it does not let you set, and it does not shrink
 * when the space is tight - it overflows, which is how the hour box ended up flush against the card
 * edge and the dial clipped at the bottom.
 *
 * So it is composed at a known design width and then scaled to whatever width it is actually given,
 * by lowering the density it lays out against. Boxes, dial, selector and text shrink together and in
 * proportion, which is why this holds on any screen rather than for one phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerCard(timeState: TimePickerState) {
    // The two type roles the picker sizes itself from: the hour and minute boxes grow with
    // displayLarge, and the AM/PM selector with titleMedium. Both get a centred line box, without
    // which a digit rides low in its square and a dial numeral sits off-centre in its circle.
    val typography = MaterialTheme.typography.copy(
        displayLarge = MaterialTheme.typography.displayLarge.centredLineBox(),
        titleMedium = MaterialTheme.typography.titleMedium.copy(fontSize = PERIOD_LABEL_SIZE).centredLineBox(),
        bodyLarge = MaterialTheme.typography.bodyLarge.centredLineBox(),
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scale = (maxWidth / TIME_PICKER_DESIGN_WIDTH).coerceAtMost(1f)
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            MaterialTheme(typography = typography) {
                TimePicker(
                    state = timeState,
                    modifier = Modifier.align(Alignment.Center),
                    colors = TimePickerDefaults.colors(
                        clockDialColor = MaterialTheme.colorScheme.surfaceVariant,
                        selectorColor = MaterialTheme.colorScheme.primary,
                        containerColor = Color.Transparent,
                        periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        periodSelectorSelectedContentColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }
}

/** Centres the text in its line box, so glyphs sit in the middle of the square or circle behind them. */
private fun androidx.compose.ui.text.TextStyle.centredLineBox(): androidx.compose.ui.text.TextStyle = copy(
    lineHeight = fontSize,
    lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None),
)

/** Width the picker is composed at before being scaled down to the width it is actually given. */
private val TIME_PICKER_DESIGN_WIDTH = 296.dp
private val PERIOD_LABEL_SIZE = 13.sp
