package dev.upyet.history.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.core.time.TimeProvider
import dev.upyet.evidence.data.EvidenceThumbnailCache
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.OccurrenceChain
import dev.upyet.evidence.domain.OccurrenceRepository
import dev.upyet.evidence.domain.buildOccurrenceChains
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter.ALL,
    val groups: List<HistoryGroup> = emptyList(),
    val isEmpty: Boolean = true,
    val isFilterEmpty: Boolean = false,
    val thumbnails: Map<String, ImageBitmap> = emptyMap(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    repository: OccurrenceRepository,
    alarmRepository: AlarmRepository,
    private val thumbnailCache: EvidenceThumbnailCache,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    data class HistoryItem(
        val chain: OccurrenceChain,
        val label: String,
        val segmentsByOccurrence: Map<OccurrenceId, List<EvidenceSegment>>,
    ) {
        val allSegments: List<EvidenceSegment> = chain.links.flatMap { segmentsByOccurrence[it.id] ?: emptyList() }
    }

    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private val thumbnails = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())

    /** Clips already asked for. Touched only from the main dispatcher, where the requests originate. */
    private val requested = mutableSetOf<String>()

    private val items = combine(repository.observeOccurrences(), alarmRepository.observeAlarms()) { occurrences, alarms ->
        occurrences to alarms
    }.flatMapLatest { (occurrences, alarms) ->
        if (occurrences.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(occurrences.map { repository.observeSegments(it.id) }) { segmentLists ->
                val segmentsByOccurrence: Map<OccurrenceId, List<EvidenceSegment>> =
                    occurrences.mapIndexed { index, occurrence -> occurrence.id to segmentLists[index].toList() }.toMap()
                val chains = buildOccurrenceChains(occurrences)
                chains.map { chain ->
                    val label = alarms.firstOrNull { it.id == chain.root.alarmId }?.label ?: ""
                    val chainSegments = chain.links.associate { it.id to (segmentsByOccurrence[it.id] ?: emptyList()) }
                    HistoryItem(chain, label, chainSegments)
                }
            }
        }
    }

    val state: StateFlow<HistoryUiState> = combine(items, filter, thumbnails) { allItems, selectedFilter, loadedThumbnails ->
        val filtered = filterHistoryItems(allItems, selectedFilter)
        HistoryUiState(
            filter = selectedFilter,
            groups = groupHistoryItemsByDay(filtered, timeProvider.zone(), timeProvider.now().atZone(timeProvider.zone()).toLocalDate()),
            isEmpty = allItems.isEmpty(),
            isFilterEmpty = allItems.isNotEmpty() && filtered.isEmpty(),
            thumbnails = loadedThumbnails,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(value: HistoryFilter) {
        filter.value = value
    }

    /** A row reporting that it is showing [fileName]. Recomposition repeats it, so each clip loads once. */
    fun onThumbnailNeeded(fileName: String) {
        if (!requested.add(fileName)) return
        viewModelScope.launch {
            val bitmap = thumbnailCache.thumbnail(fileName, durationOf(fileName)) ?: return@launch
            thumbnails.update { it + (fileName to bitmap) }
        }
    }

    /**
     * Extraction needs the clip's length to know whether a frame one second in exists at all, and the row
     * reports only its file name, so the length comes back from the state that row was rendered from.
     */
    private fun durationOf(fileName: String): Long? = state.value.groups
        .asSequence()
        .flatMap { it.items }
        .flatMap { it.allSegments }
        .firstOrNull { it.fileName == fileName }
        ?.durationMs
}
