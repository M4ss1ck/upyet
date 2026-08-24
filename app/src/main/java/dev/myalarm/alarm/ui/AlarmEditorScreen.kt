package dev.myalarm.alarm.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.myalarm.R
import dev.myalarm.alarm.domain.Recurrence
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    alarmId: Long?,
    onDone: () -> Unit,
    onReliability: () -> Unit = {},
    viewModel: AlarmEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeState = rememberTimePickerState(state.time.hour, state.time.minute, true)
    var snoozeExpanded by remember { mutableStateOf(false) }
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.update { it.copy(soundUri = uri?.toString()) }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.errorRes?.let { error ->
            Text(stringResource(error))
            if (state.showReliabilityAction) Button(onClick = onReliability) { Text(stringResource(R.string.open_reliability)) }
        }
        Text(stringResource(R.string.alarm_time))
        TimePicker(timeState)
        OutlinedTextField(state.label, { value ->
            viewModel.update { it.copy(label = value) }
        }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.alarm_label)) })
        Text(stringResource(R.string.recurrence))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.recurrence == Recurrence.OneTime, onClick = {
                viewModel.update { it.copy(recurrence = Recurrence.OneTime) }
            }, label = { Text(stringResource(R.string.one_time)) })
            FilterChip(selected = state.recurrence == Recurrence.Daily, onClick = {
                viewModel.update { it.copy(recurrence = Recurrence.Daily) }
            }, label = { Text(stringResource(R.string.daily)) })
        }
        val selectedDays = (state.recurrence as? Recurrence.Weekly)?.days.orEmpty()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = day in selectedDays,
                    onClick = {
                        val days = selectedDays.toMutableSet().apply { if (!remove(day)) add(day) }
                        if (days.isNotEmpty()) viewModel.update { it.copy(recurrence = Recurrence.Weekly(days)) }
                    },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                )
            }
        }
        SettingSwitch(stringResource(R.string.vibration), state.vibration) { value -> viewModel.update { it.copy(vibration = value) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.snooze_minutes))
            Button(onClick = { snoozeExpanded = true }) { Text(stringResource(R.string.snooze_option, state.snoozeMinutes)) }
            DropdownMenu(snoozeExpanded, { snoozeExpanded = false }) {
                listOf(5, 9, 10, 15, 30).forEach { minutes ->
                    DropdownMenuItem({ Text(stringResource(R.string.snooze_option, minutes)) }, {
                        viewModel.update { it.copy(snoozeMinutes = minutes) }
                        snoozeExpanded =
                            false
                    })
                }
            }
        }
        SettingSwitch(stringResource(R.string.evidence), state.evidence) { value -> viewModel.update { it.copy(evidence = value) } }
        Button(onClick = {
            val intent = Intent(
                RingtoneManager.ACTION_RINGTONE_PICKER,
            ).putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_ALARM,
            ).putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, state.soundUri?.let(Uri::parse))
            ringtoneLauncher.launch(intent)
        }) { Text(stringResource(R.string.ringtone)) }
        Button(onClick = {
            viewModel.update { it.copy(time = LocalTime.of(timeState.hour, timeState.minute)) }
            viewModel.save(onDone)
        }) { Text(stringResource(R.string.save)) }
        Button(onClick = onDone) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
