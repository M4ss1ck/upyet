package dev.myalarm.alarm.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.myalarm.R
import dev.myalarm.alarm.domain.Alarm
import dev.myalarm.alarm.domain.Recurrence
import dev.myalarm.core.ui.currentLocale
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AlarmListScreen(onEdit: (Long) -> Unit, onReliability: () -> Unit, viewModel: AlarmListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(floatingActionButton = {
        FloatingActionButton(onClick = { onEdit(-1) }) { Icon(Icons.Default.Add, stringResource(R.string.create_alarm)) }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.errorRes?.let { error -> item { SchedulingBanner(error, state.showReliabilityAction, onReliability) } }
            items(state.alarms, key = {
                it.id.value
            }) { alarm -> AlarmRow(alarm, { viewModel.setEnabled(alarm, it) }, { onEdit(alarm.id.value) }, { viewModel.delete(alarm.id) }) }
            if (state.alarms.isEmpty()) item { Text(stringResource(R.string.no_alarms)) }
        }
    }
}

@Composable
private fun SchedulingBanner(@StringRes message: Int, showReliability: Boolean, onReliability: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp)) {
        Icon(Icons.Default.Warning, stringResource(R.string.warning), Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
        Text(stringResource(message), Modifier.weight(1f).padding(8.dp))
        if (showReliability) TextButton(onClick = onReliability) { Text(stringResource(R.string.open_reliability)) }
    }
}

@Composable
private fun AlarmRow(alarm: Alarm, onEnabled: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showDelete by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(alarm.time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale())))
            Text(alarm.label.ifBlank { stringResource(R.string.unnamed_alarm) })
            val recurrenceText = when (val recurrence = alarm.recurrence) {
                is Recurrence.Weekly -> stringResource(
                    recurrenceSummaryResource(recurrence),
                    recurrence.days.sortedBy { it.value }.joinToString(", ") {
                        it.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    },
                )

                else -> stringResource(recurrenceSummaryResource(recurrence))
            }
            Text(recurrenceText)
        }
        val enabledDescription = stringResource(R.string.alarm_enabled_description)
        Switch(
            checked = alarm.enabled,
            onCheckedChange = onEnabled,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
                contentDescription =
                    enabledDescription
            },
        )
        IconButton(onClick = {
            showDelete = true
        }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
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

@StringRes
fun recurrenceSummary(recurrence: Recurrence): Int = when (recurrence) {
    Recurrence.OneTime -> R.string.recurrence_one_time
    Recurrence.Daily -> R.string.recurrence_daily
    is Recurrence.Weekly -> R.string.recurrence_weekdays
}

@StringRes
fun recurrenceSummaryResource(recurrence: Recurrence): Int = recurrenceSummary(recurrence)
