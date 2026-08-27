package dev.upyet.alarm.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.core.directboot.UserUnlockState
import dev.upyet.core.logging.AlarmLog
import dev.upyet.core.notifications.AlarmNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class UpcomingAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var notifications: AlarmNotifications

    @Inject lateinit var userUnlockState: UserUnlockState

    @Inject lateinit var alarmRepository: AlarmRepository

    @Inject lateinit var scheduler: AlarmScheduler

    @Inject lateinit var rescheduler: AlarmRescheduler

    @Inject lateinit var skipNextOccurrence: SkipNextOccurrence

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_UPCOMING_ALARM -> {
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
                val label = intent.getStringExtra(EXTRA_LABEL)
                val triggerAtMillis = intent.getLongExtra(EXTRA_TRIGGER_AT, -1L)
                val canSkip = intent.getBooleanExtra(EXTRA_CAN_SKIP, false)
                if (alarmId < 0 || triggerAtMillis < 0) return
                notifications.postUpcoming(alarmId, label, Instant.ofEpochMilli(triggerAtMillis), canSkip)
            }

            ACTION_UPCOMING_ALARM_SKIP -> {
                val pending = goAsync()
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                scope.launch {
                    try {
                        if (!userUnlockState.isUserUnlocked()) {
                            notifications.cancelUpcoming()
                            return@launch
                        }
                        val alarmId = AlarmId(intent.getLongExtra(EXTRA_ALARM_ID, -1L))
                        if (alarmId.value < 0) {
                            notifications.cancelUpcoming()
                            return@launch
                        }
                        val canSkip = intent.getBooleanExtra(EXTRA_CAN_SKIP, false)
                        val alarm = runCatching { alarmRepository.getAlarm(alarmId) }.getOrNull()
                        if (alarm != null) {
                            if (canSkip) {
                                val result = skipNextOccurrence.skip(alarm)
                                if (result == null) {
                                    AlarmLog.event("upcoming_alarm_error", "reason" to "skip_failed")
                                }
                            } else {
                                // One-time alarms cannot skip - turning off is the same act.
                                scheduler.cancel(alarm.id, AlarmOccurrenceKind.MAIN)
                                scheduler.cancel(alarm.id, AlarmOccurrenceKind.SNOOZE)
                                alarmRepository.setEnabled(alarm.id, false)
                            }
                        }
                        notifications.cancelUpcoming()
                        try {
                            rescheduler.refreshUpcoming()
                        } catch (error: Exception) {
                            AlarmLog.event("upcoming_alarm_error", "error" to error.javaClass.simpleName)
                        }
                    } finally {
                        pending.finish()
                        scope.cancel()
                    }
                }
            }

            else -> Unit
        }
    }

    companion object {
        const val ACTION_UPCOMING_ALARM = "dev.upyet.action.UPCOMING_ALARM"
        const val ACTION_UPCOMING_ALARM_SKIP = "dev.upyet.action.UPCOMING_ALARM_SKIP"
        const val EXTRA_ALARM_ID = "upcoming_alarm_id"
        const val EXTRA_LABEL = "upcoming_label"
        const val EXTRA_TRIGGER_AT = "upcoming_trigger_at"
        const val EXTRA_CAN_SKIP = "upcoming_can_skip"
        const val REQUEST_UPCOMING_ALARM = 3001
        const val REQUEST_UPCOMING_ALARM_SKIP = 3002
    }
}
