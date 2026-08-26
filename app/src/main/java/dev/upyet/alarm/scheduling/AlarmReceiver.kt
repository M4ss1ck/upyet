package dev.upyet.alarm.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.alarm.domain.SnoozeBudget
import dev.upyet.core.logging.AlarmLog
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var ringingLauncher: RingingLauncher

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmPendingIntents.EXTRA_ALARM_ID, -1L)
        val scheduledFor = intent.getLongExtra(AlarmPendingIntents.EXTRA_SCHEDULED_FOR, -1L)
        val kind = intent.getStringExtra(AlarmPendingIntents.EXTRA_KIND)?.let {
            runCatching { AlarmOccurrenceKind.valueOf(it) }.getOrNull()
        }
        if (alarmId < 0 || scheduledFor < 0 || kind == null) return
        val parent = intent.getLongExtra(AlarmPendingIntents.EXTRA_PARENT_OCCURRENCE_ID, -1L)
            .takeIf { it >= 0 }?.let(::OccurrenceId)
        val snoozesRemaining = intent.getIntExtra(AlarmPendingIntents.EXTRA_SNOOZES_REMAINING, SnoozeBudget.UNSET)
        val chainStartedAtMillis = intent.getLongExtra(AlarmPendingIntents.EXTRA_CHAIN_STARTED_AT, 0L)
        AlarmLog.event("alarm_triggered", "alarmId" to alarmId, "kind" to kind.name)
        ringingLauncher.launch(
            AlarmId(alarmId),
            Instant.ofEpochMilli(scheduledFor),
            kind,
            parent,
            snoozesRemaining,
            chainStartedAtMillis,
        )
    }
}
