package dev.myalarm.history.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.myalarm.alarm.domain.OccurrenceId
import dev.myalarm.evidence.data.EvidenceFileStore
import dev.myalarm.evidence.domain.DeleteOccurrenceResult
import dev.myalarm.evidence.domain.DeleteOccurrenceWithEvidence
import dev.myalarm.evidence.domain.OccurrenceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OccurrenceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: OccurrenceRepository,
    private val deleteOccurrence: DeleteOccurrenceWithEvidence,
    private val files: EvidenceFileStore,
) : ViewModel() {
    private val id = OccurrenceId(savedStateHandle.get<Long>("id") ?: returnInvalidId())
    val item: StateFlow<OccurrenceRepository.OccurrenceWithEvidence?> = repository.observeOccurrenceWithSegments(id).stateIn(
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
