package dev.upyet.timer.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.upyet.core.logging.AlarmLog
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerRunState
import dev.upyet.timer.domain.TimerTransition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The timer's one exact alarm and the platform events that re-arm it (AGENTS.md §6a). Direct-Boot aware and
 * Room-free until [TimerDiscovery] has verified the user is unlocked (§11). It does no long work: it hands
 * each delivery to [TimerDiscovery] under `goAsync`, as `BootReceiver` does, and never starts ringing itself.
 * A timer due inside the boot grace window rings through the immediate exact alarm discovery arms, because a
 * `BOOT_COMPLETED` receiver may not start a `mediaPlayback` foreground service on targetSdk 35+ (ADR 0009).
 */
@AndroidEntryPoint
class TimerReceiver : BroadcastReceiver() {
    @Inject lateinit var discovery: TimerDiscovery

    override fun onReceive(context: Context, intent: Intent) {
        val command = TimerReceiverCommand.of(intent.action) ?: return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                when (command) {
                    TimerReceiverCommand.EXPIRY -> {
                        val delivered = TimerPendingIntents.delivered(intent)
                        if (delivered == null) {
                            AlarmLog.event("timer_delivery_malformed")
                        } else {
                            discovery.onExpiry(delivered.first, delivered.second)
                        }
                    }

                    TimerReceiverCommand.BOOT -> discovery.onBoot()

                    TimerReceiverCommand.REARM -> discovery.rearm()

                    TimerReceiverCommand.WALL_CLOCK_CHANGED -> discovery.onWallClockChanged()

                    TimerReceiverCommand.ZONE_CHANGED -> discovery.onZoneChanged()

                    TimerReceiverCommand.PAUSE -> editRunning(intent, command) { timer, now -> timer.pause(now) }

                    TimerReceiverCommand.ADD_MINUTE -> editRunning(intent, command) { timer, now -> timer.addMinute(now) }

                    TimerReceiverCommand.RESET -> editRunning(intent, command) { timer, _ -> timer.reset() }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // A storage failure must not crash a boot or alarm delivery; the next launch re-arms.
                AlarmLog.event("timer_receiver_failed", "command" to command.name, "error" to error.javaClass.simpleName)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }

    /**
     * A notification action. Only a Running timer is touched: a tap that arrives after the timer paused, rang or
     * was reset is a no-op, which [TimerDiscovery.edit] enforces under its lock.
     */
    private suspend fun editRunning(intent: Intent, command: TimerReceiverCommand, transition: (Timer, TimerAnchor) -> TimerTransition) {
        val id = TimerRunningActions.timerId(intent)
        if (id == null) {
            AlarmLog.event("timer_action_malformed", "command" to command.name.lowercase())
            return
        }
        discovery.edit(id, command.name.lowercase(), onlyIf = { it.runState is TimerRunState.Running }, transition = transition)
    }
}
