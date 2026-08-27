package dev.upyet.history.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.upyet.R
import dev.upyet.core.ui.components.UpYetCard
import dev.upyet.core.ui.theme.MinTouchTarget
import dev.upyet.evidence.domain.StatsWindow
import dev.upyet.evidence.domain.WakeUpStats

@Composable
fun StatsCard(stats: WakeUpStats, window: StatsWindow, onWindowChange: (StatsWindow) -> Unit, modifier: Modifier = Modifier) {
    UpYetCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Three window labels are wider than a narrow phone, and a plain Row answers that by breaking
            // "All time" across two lines. The filter row below solves the same squeeze the same way.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val options = listOf(
                    StatsWindow.SEVEN_DAYS to R.string.stats_window_7,
                    StatsWindow.THIRTY_DAYS to R.string.stats_window_30,
                    StatsWindow.ALL_TIME to R.string.stats_window_all,
                )
                options.forEach { (entry, labelRes) ->
                    FilterChip(
                        selected = entry == window,
                        onClick = { onWindowChange(entry) },
                        label = { Text(stringResource(labelRes)) },
                        modifier = Modifier.heightIn(min = MinTouchTarget),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = entry == window,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = Color.Transparent,
                        ),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.stats_first_try_value, stats.firstTry, stats.finished),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.stats_first_try),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stats.snoozes.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.stats_snoozes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (stats.snoozedMinutes > 0) {
                        Text(
                            text = pluralStringResource(R.plurals.stats_snoozed_minutes, stats.snoozedMinutes, stats.snoozedMinutes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stats.missed.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.stats_missed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
