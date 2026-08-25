package dev.upyet.alarm.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.upyet.core.logging.AlarmLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var rescheduler: AlarmRescheduler
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> Unit

            else -> return
        }
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val report = rescheduler.rescheduleAll()
                AlarmLog.event(
                    "alarms_rescheduled",
                    "scheduled" to report.scheduled,
                    "skipped" to report.skipped,
                    "failures" to report.failures.size,
                    "exactAlarmsUnavailable" to report.exactAlarmsUnavailable,
                )
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }
}
