package dev.upyet.history.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.evidence.data.EvidenceFileStore
import dev.upyet.evidence.domain.DeleteOccurrenceWithEvidence
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.EvidenceStatus
import dev.upyet.evidence.domain.OccurrenceChain
import dev.upyet.evidence.domain.OccurrenceRepository
import dev.upyet.evidence.domain.buildOccurrenceChains
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OccurrenceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: OccurrenceRepository,
    alarmRepository: AlarmRepository,
    private val deleteOccurrence: DeleteOccurrenceWithEvidence,
    private val files: EvidenceFileStore,
) : ViewModel() {
    data class OccurrenceDetail(
        val chain: OccurrenceChain,
        val label: String,
        val segmentsByOccurrence: Map<OccurrenceId, List<EvidenceSegment>>,
    ) {
        val allSegments: List<EvidenceSegment> = chain.links.flatMap { segmentsByOccurrence[it.id] ?: emptyList() }
    }

    private val id = OccurrenceId(savedStateHandle.get<Long>("id") ?: returnInvalidId())
    val item: StateFlow<OccurrenceDetail?> = combine(
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
                    OccurrenceDetail(chain, label, segmentsByOccurrence)
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null,
        )

    fun fileUri(fileName: String): Uri = Uri.fromFile(files.resolve(fileName))

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
