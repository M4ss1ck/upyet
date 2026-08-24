package dev.myalarm.evidence.camera

import dev.myalarm.evidence.domain.EvidenceErrorCode
import dev.myalarm.evidence.domain.EvidenceStatus
import java.time.Instant

sealed interface EvidenceRecordingState {
    data object Idle : EvidenceRecordingState

    data object Preparing : EvidenceRecordingState

    data class Recording(val startedAt: Instant, val elapsedMillis: Long) : EvidenceRecordingState

    data class Finished(val status: EvidenceStatus) : EvidenceRecordingState

    data class Unavailable(val status: EvidenceStatus, val errorCode: EvidenceErrorCode) : EvidenceRecordingState
}
