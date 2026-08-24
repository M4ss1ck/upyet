package dev.myalarm.alarm.ringing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.myalarm.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun RingingScreen(state: RingingUiState, onDismiss: () -> Unit, onSnooze: () -> Unit, evidenceContent: @Composable () -> Unit = {}) {
    val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(LocalConfiguration.current.locales[0])
    val snoozeLabel = pluralStringResource(R.plurals.snooze_alarm, state.snoozeMinutes, state.snoozeMinutes)
    val dismissLabel = stringResource(R.string.dismiss_alarm)
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(formatter.format(state.currentTime), style = MaterialTheme.typography.displayLarge)
        state.label?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.headlineSmall) }
        Spacer(Modifier.height(24.dp))
        evidenceContent()
        Button(
            onClick = onSnooze,
            modifier = Modifier.fillMaxWidth().height(64.dp).semantics {
                contentDescription = snoozeLabel
            },
            content = { Text(snoozeLabel) },
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(64.dp).semantics {
                contentDescription = dismissLabel
            },
            content = { Text(dismissLabel) },
        )
    }
}
