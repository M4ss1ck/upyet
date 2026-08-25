package dev.upyet.alarm.scheduling

import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import java.time.Instant

interface RingingLauncher {
    fun launch(alarmId: AlarmId, scheduledFor: Instant, kind: AlarmOccurrenceKind, parentOccurrenceId: OccurrenceId?)
}
