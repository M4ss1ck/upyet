package dev.myalarm.alarm.scheduling

import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.OccurrenceId
import dev.myalarm.core.logging.AlarmLog
import java.time.Instant
import javax.inject.Inject

class NoOpRingingLauncher @Inject constructor() : RingingLauncher {
    // TODO(stage-4): replace this seam with the foreground playback service launcher.
    override fun launch(alarmId: AlarmId, scheduledFor: Instant, kind: AlarmOccurrenceKind, parentOccurrenceId: OccurrenceId?) {
        AlarmLog.event("alarm_launcher_unavailable", "alarmId" to alarmId.value, "kind" to kind.name)
    }
}
