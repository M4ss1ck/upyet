package dev.upyet.worldclock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.upyet.R
import dev.upyet.core.ui.components.ClockText
import dev.upyet.worldclock.data.WorldCity
import dev.upyet.worldclock.domain.WorldClockRow
import dev.upyet.worldclock.domain.worldClockRows
import java.time.Instant
import java.time.ZoneId

/**
 * The full-screen search picker. A [Dialog] rather than a route: the list underneath keeps its scroll and selection,
 * and the system back gesture closes the dialog through [onClose] exactly as the back arrow does.
 */
@Composable
internal fun CityPicker(
    picker: PickerUiState,
    now: Instant,
    homeZone: ZoneId,
    onQueryChange: (String) -> Unit,
    onAdd: (Long) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val title = stringResource(R.string.world_clock_picker_title)
        Surface(
            Modifier.fillMaxSize().testTag("world_clock_picker").semantics { paneTitle = title },
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize().imePadding()) {
                PickerSearchBar(picker.query, onQueryChange, onClose)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    PickerResults(picker, now, homeZone, onAdd)
                }
                HorizontalDivider()
                Text(
                    stringResource(R.string.world_clock_attribution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(12.dp).testTag("world_clock_attribution"),
                )
            }
        }
    }
}

@Composable
private fun PickerSearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    // Typing is held here and forwarded: a round trip through the ViewModel's flow would hand the field a stale
    // value mid-keystroke and move the cursor.
    var text by rememberSaveable { mutableStateOf(query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.world_clock_close_picker))
        }
        TextField(
            value = text,
            onValueChange = {
                text = it
                onQueryChange(it)
            },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.world_clock_search_hint)) },
            modifier = Modifier.weight(1f).focusRequester(focus).testTag("world_clock_search"),
        )
    }
}

@Composable
private fun PickerResults(picker: PickerUiState, now: Instant, homeZone: ZoneId, onAdd: (Long) -> Unit) {
    when {
        picker.query.isBlank() -> PickerMessage(stringResource(R.string.world_clock_picker_empty_query))

        picker.results.isEmpty() -> PickerMessage(stringResource(R.string.world_clock_picker_no_results))

        else -> {
            val rows = worldClockRows(picker.results.map { it.geonamesId }, picker.results.associateBy { it.geonamesId }, now, homeZone)
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows.filterIsInstance<WorldClockRow.Place>(), key = { it.geonamesId }) { row ->
                    ResultRow(row, added = row.geonamesId in picker.addedIds, onAdd = onAdd)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PickerMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultRow(row: WorldClockRow.Place, added: Boolean, onAdd: (Long) -> Unit) {
    val addedDescription = stringResource(R.string.world_clock_added)
    val city: WorldCity = row.city
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("world_clock_result_${row.geonamesId}")
            .semantics(mergeDescendants = true) { if (added) contentDescription = addedDescription }
            .let { if (added) it else it.clickable(role = Role.Button) { onAdd(city.geonamesId) } }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(placeTitle(city), style = MaterialTheme.typography.titleMedium)
            Text(dayAndOffset(row), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ClockText(row.localTime.toLocalTime(), style = MaterialTheme.typography.titleLarge)
        if (added) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}
