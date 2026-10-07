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
import dev.upyet.evidence.domain.StatsWindow
import dev.upyet.evidence.domain.WakeUpStats
import dev.upyet.evidence.domain.buildOccurrenceChains
import dev.upyet.evidence.domain.wakeUpStats
import dev.upyet.settings.data.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    val stats: WakeUpStats = WakeUpStats(0, 0, 0, 0, 0),
    val statsWindow: StatsWindow = StatsWindow.SEVEN_DAYS,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    repository: OccurrenceRepository,
    alarmRepository: AlarmRepository,
    private val thumbnailCache: EvidenceThumbnailCache,
    private val timeProvider: TimeProvider,
    private val settingsRepository: SettingsRepository,
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

    @OptIn(ExperimentalCoroutinesApi::class)
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

    private val statsWindow = settingsRepository.settings.map { it.statsWindow }

    val state: StateFlow<HistoryUiState> = combine(items, filter, thumbnails, statsWindow) {
            allItems,
            selectedFilter,
            loadedThumbnails,
            window,
        ->
        val filtered = filterHistoryItems(allItems, selectedFilter)
        val now = timeProvider.now()
        val zone = timeProvider.zone()
        val today = now.atZone(zone).toLocalDate()
        val chains = allItems.map { it.chain }
        val stats = wakeUpStats(chains, window, now)
        HistoryUiState(
            filter = selectedFilter,
            groups = groupHistoryItemsByDay(filtered, zone, today),
            isEmpty = allItems.isEmpty(),
            isFilterEmpty = allItems.isNotEmpty() && filtered.isEmpty(),
            thumbnails = loadedThumbnails,
            stats = stats,
            statsWindow = window,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(value: HistoryFilter) {
        filter.value = value
    }

    fun setStatsWindow(window: StatsWindow) {
        viewModelScope.launch { settingsRepository.setStatsWindow(window) }
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
