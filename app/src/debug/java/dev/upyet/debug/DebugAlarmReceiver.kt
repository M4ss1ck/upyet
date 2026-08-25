package dev.upyet.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.alarm.domain.Recurrence
import dev.upyet.alarm.scheduling.AlarmOccurrenceKind
import dev.upyet.alarm.scheduling.AlarmScheduler
import dev.upyet.core.logging.AlarmLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class DebugAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: AlarmRepository

    @Inject lateinit var scheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SCHEDULE) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now = Instant.now()
                val trigger = now.plusSeconds(intent.getIntExtra(EXTRA_SECONDS, DEFAULT_SECONDS).coerceAtLeast(1).toLong())
                val zone = ZoneId.systemDefault()
                val alarm = Alarm(
                    id = dev.upyet.alarm.domain.AlarmId(0),
                    time = trigger.atZone(zone).toLocalTime(),
                    enabled = true,
                    label = "Debug alarm",
                    recurrence = Recurrence.OneTime,
                    soundUri = null,
                    vibrationEnabled = true,
                    snoozeMinutes = 9,
                    evidenceEnabled = false,
                    createdAt = now,
                    updatedAt = now,
                )
                val id = repository.upsert(alarm)
                val scheduled = scheduler.schedule(alarm.copy(id = id), trigger, AlarmOccurrenceKind.MAIN)
                AlarmLog.event(
                    "debug_alarm_scheduled",
                    "alarmId" to id.value,
                    "seconds" to trigger.epochSecond - now.epochSecond,
                    "result" to scheduled,
                )
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        const val ACTION_SCHEDULE = "dev.upyet.debug.SCHEDULE"
        const val EXTRA_SECONDS = "seconds"
        const val DEFAULT_SECONDS = 60
    }
}
