package dev.upyet.alarm.playback

import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.alarm.domain.SnoozeBudget
import dev.upyet.alarm.scheduling.AlarmOccurrenceKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class RingingSession(
    val alarmId: AlarmId,
    val occurrenceId: OccurrenceId?,
    val scheduledFor: Instant,
    val triggeredAt: Instant,
    val label: String?,
    val kind: AlarmOccurrenceKind,
    val snoozeMinutes: Int,
    val evidenceEnabled: Boolean,
    val isUserUnlocked: Boolean,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = false,
    val isSilentAlarmStream: Boolean = false,
    val budget: SnoozeBudget,
    val chainStartedAt: Instant,
)

@Singleton
class RingingSessionRegistry @Inject constructor() {
    private val _session = MutableStateFlow<RingingSession?>(null)
    val session: StateFlow<RingingSession?> = _session.asStateFlow()
    internal fun update(value: RingingSession) {
        _session.value = value
    }
    internal fun clear() {
        _session.value = null
    }
}
