package dev.myalarm.history.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.myalarm.evidence.domain.AlarmOccurrence
import dev.myalarm.evidence.domain.OccurrenceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(repository: OccurrenceRepository) : ViewModel() {
    val occurrences: StateFlow<List<AlarmOccurrence>> = repository.observeOccurrences().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
}
