package dev.upyet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.upyet.alarm.scheduling.AlarmRescheduler
import dev.upyet.core.di.DefaultDispatcher
import dev.upyet.core.logging.AlarmLog
import dev.upyet.evidence.data.RetentionCleaner
import dev.upyet.timer.scheduling.TimerRearm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Runs the once-per-launch housekeeping. It lives in a ViewModel so configuration changes do not repeat it,
 * and it is deliberately not a background worker: retention is cheap and only matters while the app is used.
 */
@HiltViewModel
class MainViewModel
@Inject
constructor(
    private val retentionCleaner: RetentionCleaner,
    private val rescheduler: AlarmRescheduler,
    private val timerRearm: TimerRearm,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
    init {
        // Off the main thread from the first line, as BootReceiver does: the diagnostic log writes a file
        // synchronously and scheduling makes binder calls, and neither may delay the first frame (§17b).
        viewModelScope.launch(dispatcher) {
            // Launching is the only recovery after a force stop, which cancels every alarm and is not
            // announced by the platform (AGENTS.md §6). rescheduleAll() is idempotent, reports its own
            // counts and failures, and recomputes the upcoming-alarm notification. Only the trigger is
            // logged here, as BootReceiver does, so a diagnostic log can tell a launch from a boot.
            AlarmLog.event("launch_rescheduled")
            step("alarms") { rescheduler.rescheduleAll() }
            step("timers") { timerRearm.rearm() }
            // Evidence retention runs last: alarm reliability outranks evidence (AGENTS.md).
            step("retention") {
                val removed = retentionCleaner.clean()
                AlarmLog.event("retention_cleanup", "removed" to removed)
            }
        }
    }

    /**
     * Each step is isolated so a storage failure in one (a Room read throwing, say) neither skips the
     * others nor crashes the app on launch. The failure is logged by step and error type, never swallowed
     * silently; cancellation still propagates.
     */
    private suspend fun step(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AlarmLog.event("launch_housekeeping_failed", "step" to name, "error" to error.javaClass.simpleName)
        }
    }
}
