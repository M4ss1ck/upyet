package dev.myalarm.history.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.myalarm.evidence.domain.AlarmOccurrence
import dev.myalarm.evidence.domain.EvidenceSegment
import dev.myalarm.evidence.domain.OccurrenceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(repository: OccurrenceRepository) : ViewModel() {
    data class HistoryItem(val occurrence: AlarmOccurrence, val segments: List<EvidenceSegment>)

    val occurrences: StateFlow<List<HistoryItem>> = repository.observeOccurrences().flatMapLatest { values ->
        if (values.isEmpty()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            combine(values.map { repository.observeSegments(it.id) }) { segmentLists ->
                values.mapIndexed { index, occurrence -> HistoryItem(occurrence, segmentLists[index]) }
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
}
