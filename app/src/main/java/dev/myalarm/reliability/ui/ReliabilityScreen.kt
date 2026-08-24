package dev.myalarm.reliability.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.myalarm.R
import dev.myalarm.reliability.domain.ReliabilityCheck
import dev.myalarm.reliability.domain.ReliabilityStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun ReliabilityScreen(onBack: () -> Unit, viewModel: ReliabilityViewModel = hiltViewModel()) {
    val checks by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LazyColumn(Modifier.padding(16.dp)) {
        item { Button(onClick = onBack) { Text(stringResource(R.string.cancel)) } }
        items(checks, key = { it.id }) { check -> ReliabilityRow(check) { check.settingsIntent?.let(context::startActivity) } }
    }
}

@Composable
private fun ReliabilityRow(check: ReliabilityCheck, openSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        val icon = when (check.status) {
            ReliabilityStatus.OK -> Icons.Filled.CheckCircle
            ReliabilityStatus.WARNING -> Icons.Filled.Warning
            ReliabilityStatus.BLOCKED -> Icons.Filled.Error
        }
        val statusText = when (check.status) {
            ReliabilityStatus.OK -> R.string.status_ok
            ReliabilityStatus.WARNING -> R.string.status_warning
            ReliabilityStatus.BLOCKED -> R.string.status_blocked
        }
        Icon(icon, stringResource(statusText), Modifier.padding(12.dp))
        Text(stringResource(check.titleRes))
        val explanation = check.value?.let {
            DateTimeFormatter.ofLocalizedDateTime(
                FormatStyle.MEDIUM,
            ).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault()).format(it)
        }
        Text(
            when {
                check.id == "next_alarm" && explanation == null -> stringResource(R.string.reliability_no_next_alarm)
                explanation == null -> stringResource(check.explanationRes)
                else -> stringResource(check.explanationRes, explanation)
            },
        )
        if (check.settingsIntent != null) Button(onClick = openSettings) { Text(stringResource(R.string.open_reliability)) }
    }
}
