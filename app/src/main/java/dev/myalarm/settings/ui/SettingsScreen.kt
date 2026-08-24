package dev.myalarm.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.myalarm.BuildConfig
import dev.myalarm.R
import dev.myalarm.settings.data.RetentionPolicy

@Composable
fun SettingsScreen(onReliability: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.evidence_retention))
        Button(onClick = { expanded = true }) { Text(stringResource(retentionResource(settings?.retention ?: RetentionPolicy.SEVEN_DAYS))) }
        DropdownMenu(expanded, { expanded = false }) {
            RetentionPolicy.entries.forEach { policy ->
                DropdownMenuItem({ Text(stringResource(retentionResource(policy))) }, {
                    viewModel.setRetention(policy)
                    expanded =
                        false
                })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.default_vibration))
            Switch(
                checked =
                settings?.defaultVibrationEnabled == true,
                onCheckedChange = { viewModel.setVibration(it) },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.evidence_by_default))
            Switch(
                checked =
                settings?.evidenceEnabledByDefault == true,
                onCheckedChange = { viewModel.setEvidence(it) },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.default_snooze))
            var snoozeExpanded by remember { mutableStateOf(false) }
            val snoozeMinutes = settings?.defaultSnoozeMinutes ?: DEFAULT_SNOOZE_MINUTES
            Button(onClick = { snoozeExpanded = true }) {
                Text(pluralStringResource(R.plurals.snooze_option, snoozeMinutes, snoozeMinutes))
            }
            DropdownMenu(snoozeExpanded, { snoozeExpanded = false }) {
                SNOOZE_OPTIONS.forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text(pluralStringResource(R.plurals.snooze_option, minutes, minutes)) },
                        onClick = {
                            viewModel.setSnooze(minutes)
                            snoozeExpanded = false
                        },
                    )
                }
            }
        }
        Button(onClick = onReliability) { Text(stringResource(R.string.reliability)) }
        Text(
            text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

private const val DEFAULT_SNOOZE_MINUTES = 9
private val SNOOZE_OPTIONS = listOf(1, 5, 9, 10, 15, 20, 30)

private fun retentionResource(policy: RetentionPolicy): Int = when (policy) {
    RetentionPolicy.ONE_DAY -> R.string.retention_one_day
    RetentionPolicy.SEVEN_DAYS -> R.string.retention_seven_days
    RetentionPolicy.THIRTY_DAYS -> R.string.retention_thirty_days
    RetentionPolicy.FOREVER -> R.string.retention_forever
}
