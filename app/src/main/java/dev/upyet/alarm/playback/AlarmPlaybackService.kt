package dev.upyet.alarm.playback

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.alarm.domain.SnoozeCalculator
import dev.upyet.alarm.scheduling.AlarmOccurrenceKind
import dev.upyet.alarm.scheduling.AlarmRescheduler
import dev.upyet.alarm.scheduling.AlarmScheduler
import dev.upyet.core.directboot.AlarmMirror
import dev.upyet.core.directboot.PendingOccurrence
import dev.upyet.core.directboot.PendingOccurrenceStore
import dev.upyet.core.directboot.UserUnlockState
import dev.upyet.core.directboot.toAlarm
import dev.upyet.core.logging.AlarmLog
import dev.upyet.core.notifications.AlarmNotifications
import dev.upyet.core.time.TimeProvider
import dev.upyet.evidence.domain.OccurrenceOutcome
import dev.upyet.evidence.domain.OccurrenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Owns everything about "this alarm is ringing": audio, vibration, wake lock, the alarm notification and
 * the ringing session other components observe. It deliberately knows nothing about Compose or the camera,
 * so a crash in the ringing Activity cannot stop an alarm.
 */
@AndroidEntryPoint
class AlarmPlaybackService : Service() {
    @Inject lateinit var notifications: AlarmNotifications

    @Inject lateinit var soundPlayer: AlarmSoundPlayer

    @Inject lateinit var vibrator: AlarmVibrator

    @Inject lateinit var registry: RingingSessionRegistry

    @Inject lateinit var userUnlockState: UserUnlockState

    // Lazy: these reach credential-protected storage and must not be constructed before first unlock.
    @Inject lateinit var alarmRepository: Lazy<AlarmRepository>

    @Inject lateinit var occurrenceRepository: Lazy<OccurrenceRepository>

    @Inject lateinit var pendingOccurrences: PendingOccurrenceStore

    @Inject lateinit var mirror: AlarmMirror

    @Inject lateinit var scheduler: AlarmScheduler

    @Inject lateinit var rescheduler: Lazy<AlarmRescheduler>

    @Inject lateinit var timeProvider: TimeProvider

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var timeoutJob: Job? = null
    private var current: RingingSession? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val unlocked = userUnlockState.isUserUnlocked()
        notifications.ensureChannels()
        // Posted before any other work so the foreground-service deadline can never be missed.
        ServiceCompat.startForeground(
            this,
            AlarmNotifications.NOTIFICATION_ID_RINGING,
            notifications.buildRingingNotification(current?.label, unlocked),
            foregroundServiceType(),
        )
        when (intent?.action) {
            ACTION_START -> startRinging(intent, unlocked)
            ACTION_DISMISS -> finish(OccurrenceOutcome.DISMISSED)
            ACTION_SNOOZE -> snooze()
            else -> if (current == null) teardown()
        }
        return START_NOT_STICKY
    }

    /** Foreground-service types only exist from API 29; below that the platform expects no type at all. */
    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0

    private fun startRinging(intent: Intent, unlocked: Boolean) {
        val alarmId = AlarmId(intent.getLongExtra(EXTRA_ALARM_ID, INVALID_ID))
        val kindName = intent.getStringExtra(EXTRA_KIND)
        val kind = AlarmOccurrenceKind.entries.firstOrNull { it.name == kindName }
        if (alarmId.value == INVALID_ID || kind == null) {
            AlarmLog.event("alarm_error", "reason" to "invalid_start_intent")
            teardown()
            return
        }
        val scheduledFor = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_SCHEDULED_FOR, 0L))
        val parent = intent.getLongExtra(EXTRA_PARENT_OCCURRENCE_ID, INVALID_ID).takeIf { it >= 0 }?.let(::OccurrenceId)
        acquireWakeLock()
        serviceScope.launch {
            val triggeredAt = timeProvider.now()
            val alarm = resolveAlarm(alarmId, kind, unlocked)
            val occurrenceId = createOccurrence(alarmId, scheduledFor, triggeredAt, parent, unlocked)
            val session =
                RingingSession(
                    alarmId = alarmId,
                    occurrenceId = occurrenceId,
                    scheduledFor = scheduledFor,
                    triggeredAt = triggeredAt,
                    label = alarm?.label?.takeIf { unlocked },
                    kind = kind,
                    snoozeMinutes = alarm?.snoozeMinutes ?: DEFAULT_SNOOZE_MINUTES,
                    evidenceEnabled = alarm?.evidenceEnabled ?: false,
                    isUserUnlocked = unlocked,
                )
            current = session
            registry.update(session)
            if (unlocked) {
                getSystemService(NotificationManager::class.java)?.notify(
                    AlarmNotifications.NOTIFICATION_ID_RINGING,
                    notifications.buildRingingNotification(session.label, true),
                )
            }
            soundPlayer.start(alarm?.soundUri)
            if (alarm?.vibrationEnabled == true) vibrator.start()
            AlarmLog.event("playback_service_started", "alarmId" to alarmId.value, "unlocked" to unlocked)
            startRingingTimeout()
        }
    }

    /** Room only when unlocked; otherwise the device-protected mirror is the only source of truth. */
    private suspend fun resolveAlarm(alarmId: AlarmId, kind: AlarmOccurrenceKind, unlocked: Boolean): Alarm? = if (unlocked) {
        runCatching { alarmRepository.get().getAlarm(alarmId) }
            .onFailure { AlarmLog.event("alarm_error", "error" to it.javaClass.simpleName) }
            .getOrNull()
    } else {
        mirror.all().firstOrNull { it.alarmId == alarmId && it.kind == kind }?.toAlarm()
    }

    private suspend fun createOccurrence(
        alarmId: AlarmId,
        scheduledFor: Instant,
        triggeredAt: Instant,
        parent: OccurrenceId?,
        unlocked: Boolean,
    ): OccurrenceId? {
        if (!unlocked) {
            pendingOccurrences.append(
                PendingOccurrence(alarmId, scheduledFor, triggeredAt, null, OccurrenceOutcome.RINGING.name),
            )
            return null
        }
        return runCatching { occurrenceRepository.get().createOccurrence(alarmId, scheduledFor, triggeredAt, parent) }
            .onFailure { AlarmLog.event("alarm_error", "error" to it.javaClass.simpleName) }
            .getOrNull()
    }

    /**
     * In-process ringing timeout only. Alarm scheduling itself is always AlarmManager.setAlarmClock();
     * this coroutine merely stops an alarm nobody answered.
     */
    private fun startRingingTimeout() {
        timeoutJob?.cancel()
        timeoutJob =
            serviceScope.launch {
                delay(RINGING_TIMEOUT_MILLIS)
                finish(OccurrenceOutcome.TIMED_OUT)
            }
    }

    private fun snooze() {
        val session = current
        if (session == null) {
            teardown()
            return
        }
        // Playback stops first: snoozing must never wait on persistence or scheduling.
        stopPlayback()
        timeoutJob?.cancel()
        serviceScope.launch {
            try {
                complete(session, OccurrenceOutcome.SNOOZED, dismissedAt = null)
                scheduleSnooze(session)
                AlarmLog.event("alarm_snoozed", "alarmId" to session.alarmId.value)
            } catch (error: Exception) {
                AlarmLog.event("alarm_error", "error" to error.javaClass.simpleName)
            } finally {
                teardown()
            }
        }
    }

    /** Works before first unlock too: the alarm is then reconstructed from the device-protected mirror. */
    private suspend fun scheduleSnooze(session: RingingSession) {
        val alarm = resolveAlarm(session.alarmId, session.kind, session.isUserUnlocked) ?: return
        val triggerAt = SnoozeCalculator.snoozeAt(timeProvider.now(), session.snoozeMinutes)
        val result = scheduler.schedule(alarm, triggerAt, AlarmOccurrenceKind.SNOOZE, session.occurrenceId)
        if (result !is dev.upyet.alarm.scheduling.SchedulingResult.Scheduled) {
            AlarmLog.event("alarm_error", "reason" to "snooze_not_scheduled", "result" to result.javaClass.simpleName)
        }
    }

    private fun finish(outcome: OccurrenceOutcome) {
        val session = current
        if (session == null) {
            teardown()
            return
        }
        stopPlayback()
        timeoutJob?.cancel()
        serviceScope.launch {
            try {
                val dismissedAt = if (outcome == OccurrenceOutcome.DISMISSED) timeProvider.now() else null
                complete(session, outcome, dismissedAt)
                // The alarm has rung, so a one-time one is spent. Before first unlock the alarm store is
                // out of reach; the pending occurrence carries it, and UserUnlockedReceiver retires it there.
                if (session.isUserUnlocked) rescheduler.get().retireIfOneTime(session.alarmId)
                rescheduler.get().rescheduleAll()
                AlarmLog.event(
                    if (outcome == OccurrenceOutcome.TIMED_OUT) "alarm_timed_out" else "alarm_dismissed",
                    "alarmId" to session.alarmId.value,
                )
            } catch (error: Exception) {
                AlarmLog.event("alarm_error", "error" to error.javaClass.simpleName)
            } finally {
                teardown()
            }
        }
    }

    private suspend fun complete(session: RingingSession, outcome: OccurrenceOutcome, dismissedAt: Instant?) {
        val occurrenceId = session.occurrenceId
        if (occurrenceId != null) {
            occurrenceRepository.get().completeOccurrence(occurrenceId, dismissedAt, outcome)
        } else {
            pendingOccurrences.append(
                PendingOccurrence(session.alarmId, session.scheduledFor, session.triggeredAt, dismissedAt, outcome.name),
            )
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock =
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                acquire(WAKE_LOCK_TIMEOUT_MILLIS)
            }
    }

    private fun stopPlayback() {
        soundPlayer.stop()
        vibrator.stop()
    }

    private fun teardown() {
        registry.clear()
        current = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopPlayback()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        registry.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.upyet.action.PLAYBACK_START"
        const val ACTION_DISMISS = "dev.upyet.action.PLAYBACK_DISMISS"
        const val ACTION_SNOOZE = "dev.upyet.action.PLAYBACK_SNOOZE"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_SCHEDULED_FOR = "scheduled_for"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_PARENT_OCCURRENCE_ID = "parent_occurrence_id"
        private const val INVALID_ID = -1L
        private const val DEFAULT_SNOOZE_MINUTES = 9
        private const val RINGING_TIMEOUT_MILLIS = 5 * 60 * 1000L
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 6 * 60 * 1000L
        private const val WAKE_LOCK_TAG = "UpYet:ringing"

        fun startIntent(
            context: Context,
            alarmId: AlarmId,
            scheduledFor: Instant,
            kind: AlarmOccurrenceKind,
            parentOccurrenceId: OccurrenceId?,
        ): Intent = Intent(context, AlarmPlaybackService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_ALARM_ID, alarmId.value)
            .putExtra(EXTRA_SCHEDULED_FOR, scheduledFor.toEpochMilli())
            .putExtra(EXTRA_KIND, kind.name)
            .putExtra(EXTRA_PARENT_OCCURRENCE_ID, parentOccurrenceId?.value ?: INVALID_ID)

        fun dismissIntent(context: Context): Intent = Intent(context, AlarmPlaybackService::class.java).setAction(ACTION_DISMISS)

        fun snoozeIntent(context: Context): Intent = Intent(context, AlarmPlaybackService::class.java).setAction(ACTION_SNOOZE)
    }
}
