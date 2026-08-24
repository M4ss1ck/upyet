package dev.myalarm.alarm.scheduling

import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.OccurrenceId
import java.time.Instant

interface RingingLauncher {
    fun launch(alarmId: AlarmId, scheduledFor: Instant, kind: AlarmOccurrenceKind, parentOccurrenceId: OccurrenceId?)
}
