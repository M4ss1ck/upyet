package dev.upyet.core.directboot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.upyet.alarm.scheduling.AlarmRescheduler
import dev.upyet.core.logging.AlarmLog
import dev.upyet.evidence.domain.OccurrenceOutcome
import dev.upyet.evidence.domain.OccurrenceRepository
import dev.upyet.timer.scheduling.TimerDiscovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UserUnlockedReceiver : BroadcastReceiver() {
    @Inject lateinit var pendingStore: PendingOccurrenceStore

    @Inject lateinit var occurrenceRepository: OccurrenceRepository

    @Inject lateinit var rescheduler: AlarmRescheduler

    @Inject lateinit var userUnlockState: UserUnlockState

    @Inject lateinit var timerDiscovery: TimerDiscovery

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_UNLOCKED) return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                if (!userUnlockState.isUserUnlocked()) return@launch
                reconcileAlarms()
            } finally {
                // Timers have their own mirror, so a timer record never reaches the alarm code. After the
                // alarms, because alarm reliability outranks timers, and even when they throw. Discovery
                // reconciles the mirror into Room first, then moves overdue timers to Missed and re-arms.
                reconcileTimers()
                pending.finish()
                scope.cancel()
            }
        }
    }

    private suspend fun reconcileAlarms() {
        pendingStore.all().forEach { record ->
            val id = occurrenceRepository.createOccurrence(record.alarmId, record.scheduledFor, record.triggeredAt, null)
            val outcome = runCatching { OccurrenceOutcome.valueOf(record.outcome) }.getOrDefault(OccurrenceOutcome.ERROR)
            occurrenceRepository.completeOccurrence(id, record.dismissedAt, outcome)
            // These alarms rang before first unlock, so the service could not reach the alarm store
            // to retire them. A snoozed one is still due to ring and keeps its place.
            if (outcome != OccurrenceOutcome.SNOOZED) rescheduler.retireIfOneTime(record.alarmId)
        }
        pendingStore.clear()
        rescheduler.rescheduleAll()
    }

    private suspend fun reconcileTimers() {
        try {
            when (val result = timerDiscovery.onUserUnlocked()) {
                TimerReconciliation.Locked -> AlarmLog.event("timer_mirror_reconcile_skipped", "reason" to "locked")

                is TimerReconciliation.Done ->
                    AlarmLog.event("timer_mirror_reconciled", "applied" to result.applied, "dropped" to result.dropped)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AlarmLog.event("timer_mirror_reconcile_failed", "error" to error.javaClass.simpleName)
        }
    }
}
