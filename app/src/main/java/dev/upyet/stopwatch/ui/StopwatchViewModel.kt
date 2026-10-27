package dev.upyet.stopwatch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.upyet.stopwatch.data.StopwatchController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Keeps the flow alive for a while after the last collector leaves, so a quick tab switch does not restart it. */
private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The Stopwatch tab's state. It never ticks: the display frame loop lives in the screen, and every command goes
 * through [StopwatchController], which owns the stored anchor and the ongoing notice (AGENTS.md §13).
 */
@HiltViewModel
class StopwatchViewModel @Inject constructor(private val controller: StopwatchController) : ViewModel() {
    init {
        // Resets a stopwatch from an earlier boot and restores the notice a force stop cleared. See StopwatchController.sync.
        viewModelScope.launch { controller.sync() }
    }

    val state: StateFlow<StopwatchUiState> =
        controller.stopwatch
            .map(::StopwatchUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StopwatchUiState())

    fun onStart() {
        viewModelScope.launch { controller.start() }
    }

    fun onPause() {
        viewModelScope.launch { controller.pause() }
    }

    fun onLap() {
        viewModelScope.launch { controller.lap() }
    }

    fun onReset() {
        viewModelScope.launch { controller.reset() }
    }
}
