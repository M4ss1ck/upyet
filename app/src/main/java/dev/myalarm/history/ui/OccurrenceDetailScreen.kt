package dev.myalarm.history.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.myalarm.R

@Composable
fun OccurrenceDetailScreen() {
    Column(Modifier.padding(16.dp)) { Text(stringResource(R.string.evidence_segments)) }
}
