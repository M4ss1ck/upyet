package dev.upyet.timer.playback

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.upyet.core.logging.AlarmLog
import dev.upyet.core.notifications.AlarmNotifications
import dev.upyet.core.time.TimerClock
import dev.upyet.timer.domain.TimerId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns everything about "timers are ringing" (AGENTS.md §8a, ADR 0008): one sound, one vibration, one wake lock
 * and one notification for every ringing timer, and the Stop, +1 min and Stop all commands. It is not
 * `AlarmPlaybackService` and shares nothing with it but [TimerSoundAndVibration]'s two classes. It knows
 * nothing about Compose, Room or the camera, so it keeps ringing if the ringing screen crashes, and Stop works
 * whatever the camera is doing. It never calls `cancelUpcoming()`: a timer ending says nothing about the next
 * alarm (ADR 0012).
 *
 * Commands run one at a time, in arrival order, on one consumer of [commands]: a Stop that suspends on storage
 * cannot be overtaken by the ring that follows it. The service stops itself only when nothing rings and the
 * command it just ran is the latest one it was given, so a ring arriving during a Stop all is never lost.
 */
@AndroidEntryPoint
class TimerPlaybackService : Service() {
    @Inject lateinit var notifications: TimerNotifications

    @Inject lateinit var alarmNotifications: AlarmNotifications

    @Inject lateinit var registry: TimerRingingRegistry

    @Inject lateinit var store: TimerRingingStore

    @Inject lateinit var clock: TimerClock

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val commands = Channel<Pair<Command, Int>>(Channel.UNLIMITED)
    private lateinit var output: TimerSoundAndVibration
    private lateinit var ringing: TimerRinging
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        output = TimerSoundAndVibration(this)
        ringing = TimerRinging(registry, store, output, clock)
        serviceScope.launch {
            for ((command, startId) in commands) {
                // One consumer serves every command: if it died, the next Stop would be dropped while the sound plays.
                try {
                    run(command, startId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    AlarmLog.event("timer_error", "reason" to "command_failed", "error" to error.javaClass.simpleName)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        alarmNotifications.ensureChannels()
        // Posted before any other work so the foreground-service deadline can never be missed.
        ServiceCompat.startForeground(
            this,
            AlarmNotifications.NOTIFICATION_ID_TIMER_RINGING,
            notifications.build(registry.ringing.value),
            foregroundServiceType(),
        )
        val command = Command.of(intent)
        if (command is Command.Ring || registry.ringing.value.isNotEmpty()) acquireWakeLock()
        commands.trySend((command ?: Command.Unknown) to startId)
        return START_NOT_STICKY
    }

    private suspend fun run(command: Command, startId: Int) {
        val now = when (command) {
            is Command.Ring -> ringing.ring(command.timerId)
            is Command.Stop -> ringing.stop(command.timerId)
            is Command.AddMinute -> ringing.addMinute(command.timerId)
            Command.StopAll -> ringing.stopAll()
            Command.Unknown -> registry.ringing.value.also { AlarmLog.event("timer_error", "reason" to "invalid_command") }
        }
        if (now.isNotEmpty()) {
            acquireWakeLock()
            getSystemService(NotificationManager::class.java)?.notify(
                AlarmNotifications.NOTIFICATION_ID_TIMER_RINGING,
                notifications.build(now),
            )
        } else {
            releaseWakeLock()
            if (stopSelfResult(startId)) AlarmLog.event("timer_ringing_ended")
        }
    }

    /** Foreground-service types only exist from API 29; below that the platform expects no type at all. */
    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0

    /**
     * One lock for every ringing timer (ADR 0008), held while any timer rings and released with the last. A timer
     * rings until it is stopped, so the timeout is a cap against a lock that outlives a bug, renewed by every
     * command; while audio plays the platform's audio server holds its own wake lock, so a ring left unanswered
     * past the cap keeps sounding (reasoned, not yet observed on a device).
     */
    private fun acquireWakeLock() {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        lock.acquire(WAKE_LOCK_TIMEOUT_MILLIS)
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        commands.close()
        serviceScope.cancel()
        // A destroyed service must not leave a timer sounding, nor a showing a later ring would join without a
        // sound. The service only stops itself once nothing rings and no newer command is queued, so in practice
        // both are already empty here; a new instance is only created after this returns.
        if (::output.isInitialized) output.stop()
        registry.clear()
        releaseWakeLock()
        wakeLock = null
        super.onDestroy()
    }

    private sealed interface Command {
        data class Ring(val timerId: TimerId) : Command

        data class Stop(val timerId: TimerId) : Command

        data class AddMinute(val timerId: TimerId) : Command

        data object StopAll : Command

        data object Unknown : Command

        companion object {
            fun of(intent: Intent?): Command? {
                val id = intent?.getLongExtra(EXTRA_TIMER_ID, INVALID_ID)?.takeIf { it != INVALID_ID }?.let(::TimerId)
                return when (intent?.action) {
                    ACTION_RING -> id?.let(::Ring)
                    ACTION_STOP -> id?.let(::Stop)
                    ACTION_ADD_MINUTE -> id?.let(::AddMinute)
                    ACTION_STOP_ALL -> StopAll
                    else -> null
                }
            }
        }
    }

    companion object {
        const val ACTION_RING = "dev.upyet.action.TIMER_RING"
        const val ACTION_STOP = "dev.upyet.action.TIMER_STOP"
        const val ACTION_ADD_MINUTE = "dev.upyet.action.TIMER_ADD_MINUTE"
        const val ACTION_STOP_ALL = "dev.upyet.action.TIMER_STOP_ALL"
        private const val EXTRA_TIMER_ID = "timer_id"
        private const val INVALID_ID = -1L
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 30 * 60 * 1000L
        private const val WAKE_LOCK_TAG = "UpYet:timer_ringing"

        fun ringIntent(context: Context, timerId: TimerId): Intent = command(context, ACTION_RING).putExtra(EXTRA_TIMER_ID, timerId.value)

        fun stopIntent(context: Context, timerId: TimerId): Intent = command(context, ACTION_STOP).putExtra(EXTRA_TIMER_ID, timerId.value)

        fun addMinuteIntent(context: Context, timerId: TimerId): Intent =
            command(context, ACTION_ADD_MINUTE).putExtra(EXTRA_TIMER_ID, timerId.value)

        fun stopAllIntent(context: Context): Intent = command(context, ACTION_STOP_ALL)

        private fun command(context: Context, action: String): Intent = Intent(context, TimerPlaybackService::class.java).setAction(action)
    }
}
