package dev.myalarm.alarm.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.myalarm.R
import dev.myalarm.alarm.domain.Recurrence
import dev.myalarm.core.ui.currentLocale
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle

private val MIN_TOUCH_TARGET = 48.dp
private val SNOOZE_OPTIONS = listOf(5, 9, 10, 15, 30)

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
            TopAppBar(
                title = {
                    Text(stringResource(if (isNewAlarm) R.string.create_alarm else R.string.edit_alarm))
                },
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
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET),
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = onSave,
                            enabled = state.isLoaded,
                            modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET),
                        ) {
                            Text(stringResource(R.string.save))
                        }
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
    val ringtoneLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                onUpdate { it.copy(soundUri = uri?.toString()) }
            }
        }
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.errorRes?.let { error ->
            Text(stringResource(error), color = MaterialTheme.colorScheme.error)
            if (state.showReliabilityAction) {
                Button(onClick = onReliability) { Text(stringResource(R.string.open_reliability)) }
            }
        }

        Text(stringResource(R.string.alarm_time), style = MaterialTheme.typography.labelLarge)
        // The picker is created once the alarm has loaded, keyed on that time: a picker remembered before
        // the stored alarm arrived would keep showing the default 07:00 while the alarm is something else.
        key(state.time) {
            val timeState = rememberTimePickerState(state.time.hour, state.time.minute, true)
            LaunchedEffect(timeState.hour, timeState.minute) {
                val picked = LocalTime.of(timeState.hour, timeState.minute)
                if (picked != state.time) onUpdate { it.copy(time = picked) }
            }
            TimePicker(timeState)
        }

        OutlinedTextField(
            value = state.label,
            onValueChange = { value -> onUpdate { it.copy(label = value) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.alarm_label)) },
            singleLine = true,
        )

        Text(stringResource(R.string.recurrence), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.recurrence == Recurrence.OneTime,
                onClick = { onUpdate { it.copy(recurrence = Recurrence.OneTime) } },
                label = { Text(stringResource(R.string.one_time)) },
            )
            FilterChip(
                selected = state.recurrence == Recurrence.Daily,
                onClick = { onUpdate { it.copy(recurrence = Recurrence.Daily) } },
                label = { Text(stringResource(R.string.daily)) },
            )
        }
        val selectedDays = (state.recurrence as? Recurrence.Weekly)?.days.orEmpty()
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
                )
            }
        }

        SettingSwitch(stringResource(R.string.vibration), state.vibration) { value ->
            onUpdate { it.copy(vibration = value) }
        }
        SettingSwitch(stringResource(R.string.evidence), state.evidence) { value ->
            onUpdate { it.copy(evidence = value) }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.snooze_minutes))
            var snoozeExpanded by remember { mutableStateOf(false) }
            Button(onClick = { snoozeExpanded = true }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                Text(pluralStringResource(R.plurals.snooze_option, state.snoozeMinutes, state.snoozeMinutes))
            }
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

        Button(
            onClick = {
                val intent =
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, state.soundUri?.let(Uri::parse))
                ringtoneLauncher.launch(intent)
            },
            modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
        ) {
            Text(stringResource(R.string.ringtone))
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
