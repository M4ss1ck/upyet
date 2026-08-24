package dev.myalarm.history.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.myalarm.R
import dev.myalarm.evidence.domain.AlarmOccurrence
import dev.myalarm.evidence.domain.OccurrenceOutcome
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun HistoryScreen(onOpen: (Long) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val occurrences by viewModel.occurrences.collectAsStateWithLifecycle()
    val formatter = DateTimeFormatter.ofLocalizedDateTime(
        FormatStyle.MEDIUM,
    ).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault())
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        items(occurrences, key = { it.id.value }) { occurrence -> OccurrenceRow(occurrence, formatter) { onOpen(occurrence.id.value) } }
    }
}

@Composable
private fun OccurrenceRow(occurrence: AlarmOccurrence, formatter: DateTimeFormatter, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Column {
            Text(formatter.format(occurrence.scheduledFor))
            Text(stringResource(outcomeResource(occurrence.outcome)))
        }
    }
}

private fun outcomeResource(outcome: OccurrenceOutcome): Int = when (outcome) {
    OccurrenceOutcome.RINGING -> R.string.outcome_ringing
    OccurrenceOutcome.DISMISSED -> R.string.outcome_dismissed
    OccurrenceOutcome.SNOOZED -> R.string.outcome_snoozed
    OccurrenceOutcome.INTERRUPTED -> R.string.outcome_interrupted
    OccurrenceOutcome.TIMED_OUT -> R.string.outcome_timed_out
    OccurrenceOutcome.ERROR -> R.string.outcome_error
}
