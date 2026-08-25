package dev.upyet.history.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.core.time.TimeProvider
import dev.upyet.evidence.domain.AlarmOccurrence
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.OccurrenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter.ALL,
    val groups: List<HistoryGroup> = emptyList(),
    val isEmpty: Boolean = true,
    val isFilterEmpty: Boolean = false,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    repository: OccurrenceRepository,
    alarmRepository: AlarmRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    data class HistoryItem(val occurrence: AlarmOccurrence, val label: String, val segments: List<EvidenceSegment>)

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    private val items = combine(repository.observeOccurrences(), alarmRepository.observeAlarms()) { occurrences, alarms ->
        occurrences to alarms
    }.flatMapLatest { (occurrences, alarms) ->
        if (occurrences.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(occurrences.map { repository.observeSegments(it.id) }) { segmentLists ->
                occurrences.mapIndexed { index, occurrence ->
                    val label = alarms.firstOrNull { it.id == occurrence.alarmId }?.label ?: ""
                    HistoryItem(occurrence, label, segmentLists[index])
                }
            }
        }
    }

    val state: StateFlow<HistoryUiState> = combine(items, filter) { allItems, selectedFilter ->
        val filtered = filterHistoryItems(allItems, selectedFilter)
        HistoryUiState(
            filter = selectedFilter,
            groups = groupHistoryItemsByDay(filtered, timeProvider.zone(), timeProvider.now().atZone(timeProvider.zone()).toLocalDate()),
            isEmpty = allItems.isEmpty(),
            isFilterEmpty = allItems.isNotEmpty() && filtered.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(value: HistoryFilter) {
        filter.value = value
    }
}
