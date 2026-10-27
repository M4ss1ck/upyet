package dev.upyet.worldclock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.upyet.R
import dev.upyet.core.ui.currentLocale
import dev.upyet.worldclock.domain.WorldClockRow

/** Everything the World clock content can ask of its ViewModel, bundled so the content composable stays readable. */
internal class WorldClockCallbacks(
    val onSelect: (Long) -> Unit = {},
    val onAdd: (Long) -> Unit = {},
    val onRemove: (Long) -> Unit = {},
    val onUndoRemove: () -> Unit = {},
    val onUndoDismissed: () -> Unit = {},
    val onReorder: (List<Long>) -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
)

/** The World clock tab. Collects [WorldClockViewModel] state and re-reads the clock whenever the tab comes back. */
@Composable
fun WorldClockScreen(modifier: Modifier = Modifier, viewModel: WorldClockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onRefresh()
        onPauseOrDispose {}
    }
    val callbacks = remember(viewModel) {
        WorldClockCallbacks(
            onSelect = viewModel::onSelect,
            onAdd = viewModel::onAdd,
            onRemove = viewModel::onRemove,
            onUndoRemove = viewModel::onUndoRemove,
            onUndoDismissed = viewModel::onUndoDismissed,
            onReorder = viewModel::onReorder,
            onQueryChange = viewModel::onQueryChange,
        )
    }
    WorldClockContent(state, picker, callbacks, modifier)
}

@Composable
internal fun WorldClockContent(
    state: WorldClockUiState,
    picker: PickerUiState,
    callbacks: WorldClockCallbacks,
    modifier: Modifier = Modifier,
) {
    when (state) {
        WorldClockUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.testTag("world_clock_loading"))
        }

        is WorldClockUiState.Ready -> ReadyContent(state, picker, callbacks, modifier)
    }
}

@Composable
private fun ReadyContent(state: WorldClockUiState.Ready, picker: PickerUiState, callbacks: WorldClockCallbacks, modifier: Modifier) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    UndoSnackbar(state, snackbar, callbacks)
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { pickerOpen = true }, modifier = Modifier.testTag("world_clock_add")) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.world_clock_add_place))
            }
        },
    ) { padding ->
        WorldClockList(state, callbacks, Modifier.fillMaxSize().padding(padding))
    }
    if (pickerOpen) {
        val close = {
            pickerOpen = false
            callbacks.onQueryChange("")
        }
        CityPicker(
            picker = picker,
            now = state.now,
            homeZone = state.homeZone,
            onQueryChange = callbacks.onQueryChange,
            onAdd = {
                callbacks.onAdd(it)
                close()
            },
            onClose = close,
        )
    }
}

/**
 * Offers undo for the last removal. The removed row is already gone from [state] when this runs, so its name comes
 * from the rows seen on earlier frames.
 */
@Composable
private fun UndoSnackbar(state: WorldClockUiState.Ready, snackbar: SnackbarHostState, callbacks: WorldClockCallbacks) {
    val locale = currentLocale()
    val names = remember { HashMap<Long, String>() }
    SideEffect {
        state.rows.forEach { item ->
            (item.row as? WorldClockRow.Place)?.let { names[it.geonamesId] = cityName(it.city, locale) }
        }
    }
    val undone = stringResource(R.string.world_clock_undo)
    val generic = stringResource(R.string.world_clock_removed_generic)
    val removed = state.pendingUndo
    val message = removed?.let { names[it.geonamesId] }?.let { stringResource(R.string.world_clock_removed, it) } ?: generic
    LaunchedEffect(removed) {
        if (removed == null) return@LaunchedEffect
        when (snackbar.showSnackbar(message, actionLabel = undone, duration = SnackbarDuration.Long)) {
            SnackbarResult.ActionPerformed -> callbacks.onUndoRemove()
            SnackbarResult.Dismissed -> callbacks.onUndoDismissed()
        }
    }
}
