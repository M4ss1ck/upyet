package dev.upyet.history.ui

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.evidence.data.EvidenceFileStore
import dev.upyet.evidence.data.EvidenceSharer
import dev.upyet.evidence.domain.DeleteOccurrenceWithEvidence
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.EvidenceStatus
import dev.upyet.evidence.domain.OccurrenceChain
import dev.upyet.evidence.domain.OccurrenceRepository
import dev.upyet.evidence.domain.buildOccurrenceChains
import dev.upyet.evidence.domain.shareableClips
import dev.upyet.settings.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class OccurrenceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: OccurrenceRepository,
    alarmRepository: AlarmRepository,
    private val deleteOccurrence: DeleteOccurrenceWithEvidence,
    private val files: EvidenceFileStore,
    private val settingsRepository: SettingsRepository,
    private val sharer: EvidenceSharer,
) : ViewModel() {
    data class OccurrenceDetail(
        val chain: OccurrenceChain,
        val label: String,
        val segmentsByOccurrence: Map<OccurrenceId, List<EvidenceSegment>>,
        val isShareable: Boolean = false,
        val shareableFileNames: Set<String> = emptySet(),
    ) {
        val allSegments: List<EvidenceSegment> = chain.links.flatMap { segmentsByOccurrence[it.id] ?: emptyList() }
    }

    private val zone = ZoneId.systemDefault()

    private val id = OccurrenceId(savedStateHandle.get<Long>("id") ?: returnInvalidId())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val baseItem: StateFlow<OccurrenceDetail?> = combine(
        repository.observeOccurrences(),
        alarmRepository.observeAlarms(),
    ) { occurrences, alarms -> occurrences to alarms }
        .flatMapLatest { (occurrences, alarms) ->
            val chains = buildOccurrenceChains(occurrences)
            val chain = chains.firstOrNull { candidate -> candidate.links.any { it.id == id } }
            if (chain == null) {
                flowOf(null)
            } else {
                combine(chain.links.map { repository.observeSegments(it.id) }) { segmentArrays ->
                    val segmentsByOccurrence: Map<OccurrenceId, List<EvidenceSegment>> =
                        chain.links.mapIndexed { index, link -> link.id to segmentArrays[index].toList() }.toMap()
                    val label = alarms.firstOrNull { it.id == chain.root.alarmId }?.label ?: ""
                    OccurrenceDetail(chain, label, segmentsByOccurrence, false)
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null,
        )

    /** The share action needs to know the clips are actually on disk, so the check is a suspending map, not a predicate. */
    val item: StateFlow<OccurrenceDetail?> = baseItem.map { base ->
        if (base == null) return@map null
        val clips = shareableClips(base.chain, base.segmentsByOccurrence, zone)
        if (clips.isEmpty()) return@map base.copy(isShareable = false, shareableFileNames = emptySet())
        val existing = withContext(Dispatchers.IO) {
            clips.filter { files.resolve(it.fileName).exists() }.map { it.fileName }.toSet()
        }
        base.copy(isShareable = existing.isNotEmpty(), shareableFileNames = existing)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    val shareExplainerShown: StateFlow<Boolean> = settingsRepository.settings
        .map { it.shareExplainerShown }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun markShareExplainerShown() = viewModelScope.launch {
        settingsRepository.setShareExplainerShown()
    }

    fun fileUri(fileName: String): Uri = Uri.fromFile(files.resolve(fileName))

    fun share(summary: String, onIntent: (Intent) -> Unit) {
        val detail = item.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val clips = shareableClips(detail.chain, detail.segmentsByOccurrence, zone)
            val intent = sharer.shareIntent(clips, summary) ?: return@launch
            withContext(Dispatchers.Main) { onIntent(intent) }
        }
    }

    fun shareSegment(segment: EvidenceSegment, summary: String, onIntent: (Intent) -> Unit) {
        val detail = item.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val clips = shareableClips(detail.chain, detail.segmentsByOccurrence, zone)
            val matching = clips.firstOrNull { it.fileName == segment.fileName } ?: return@launch
            val intent = sharer.shareIntent(listOf(matching), summary) ?: return@launch
            withContext(Dispatchers.Main) { onIntent(intent) }
        }
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        val chain = item.value?.chain ?: return@launch
        // Every link is checked before anything is deleted. Deleting link by link would leave a
        // half-erased wake-up behind the moment one of them is still recording, and a refused delete
        // is the better failure.
        val recording = chain.links.any { link ->
            repository.observeSegments(link.id).first().any {
                it.status == EvidenceStatus.REQUESTED || it.status == EvidenceStatus.RECORDING
            }
        }
        if (recording) return@launch
        chain.links.forEach { deleteOccurrence(it.id) }
        onDeleted()
    }

    private fun returnInvalidId(): Long = 0L
}
