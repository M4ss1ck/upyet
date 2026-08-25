package dev.upyet.history.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.evidence.data.EvidenceFileStore
import dev.upyet.evidence.domain.AlarmOccurrence
import dev.upyet.evidence.domain.DeleteOccurrenceResult
import dev.upyet.evidence.domain.DeleteOccurrenceWithEvidence
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.OccurrenceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OccurrenceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: OccurrenceRepository,
    alarmRepository: AlarmRepository,
    private val deleteOccurrence: DeleteOccurrenceWithEvidence,
    private val files: EvidenceFileStore,
) : ViewModel() {
    data class OccurrenceDetail(val occurrence: AlarmOccurrence, val label: String, val segments: List<EvidenceSegment>)

    private val id = OccurrenceId(savedStateHandle.get<Long>("id") ?: returnInvalidId())
    val item: StateFlow<OccurrenceDetail?> = combine(
        repository.observeOccurrenceWithSegments(id),
        alarmRepository.observeAlarms(),
    ) { value, alarms ->
        value?.let {
            OccurrenceDetail(it.occurrence, alarms.firstOrNull { alarm -> alarm.id == it.occurrence.alarmId }?.label ?: "", it.segments)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    fun fileUri(fileName: String): Uri = Uri.fromFile(files.resolve(fileName))

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        if (deleteOccurrence(id) is DeleteOccurrenceResult.Deleted) onDeleted()
    }

    private fun returnInvalidId(): Long = 0L
}
