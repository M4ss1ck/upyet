package dev.myalarm.core.directboot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.myalarm.alarm.scheduling.AlarmRescheduler
import dev.myalarm.evidence.domain.OccurrenceOutcome
import dev.myalarm.evidence.domain.OccurrenceRepository
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
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                if (!userUnlockState.isUserUnlocked()) return@launch
                pendingStore.all().forEach { record ->
                    val id = occurrenceRepository.createOccurrence(record.alarmId, record.scheduledFor, record.triggeredAt, null)
                    occurrenceRepository.completeOccurrence(
                        id,
                        record.dismissedAt,
                        runCatching {
                            OccurrenceOutcome.valueOf(record.outcome)
                        }.getOrDefault(OccurrenceOutcome.ERROR),
                    )
                }
                pendingStore.clear()
                rescheduler.rescheduleAll()
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }
}
