package dev.upyet.stopwatch.data

import dev.upyet.core.time.TimerClock
import dev.upyet.stopwatch.domain.BootCount
import dev.upyet.stopwatch.domain.Stopwatch
import dev.upyet.stopwatch.domain.StopwatchNotices
import dev.upyet.stopwatch.domain.StopwatchStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every stopwatch command goes through here, so the stored anchor and the ongoing notice never disagree. Each
 * command first [Stopwatch.reconcile]s against the current boot, then applies, persists and re-syncs the notice
 * under one lock, so two quick taps cannot post the notice for a state that was already replaced. Re-syncing
 * posts under the one fixed id 1104, so it is idempotent and never duplicates the notice.
 */
@Singleton
class StopwatchController @Inject constructor(
    private val store: StopwatchStore,
    private val clock: TimerClock,
    private val bootCount: BootCount,
    private val notices: StopwatchNotices,
) {
    private val lock = Mutex()

    val stopwatch: Flow<Stopwatch> get() = store.stopwatch

    /**
     * Resets a stopwatch left over from an earlier boot and restores the notice a force stop cleared. Called when
     * the stopwatch screen opens, the only moment after a force stop the app learns it should count again.
     */
    suspend fun sync(): Stopwatch = execute { _, _ -> this }

    suspend fun start(): Stopwatch = execute { now, boot -> start(now, boot) }

    suspend fun pause(): Stopwatch = execute { now, _ -> pause(now) }

    suspend fun lap(): Stopwatch = execute { now, _ -> lap(now) }

    suspend fun reset(): Stopwatch = execute { _, _ -> reset() }

    private suspend fun execute(command: Stopwatch.(now: Duration, boot: Int?) -> Stopwatch): Stopwatch = lock.withLock {
        val now = clock.now()
        val boot = bootCount.current()
        val next = store.update { it.reconcile(now.elapsedRealtime, boot).command(now.elapsedRealtime, boot) }
        if (next.isRunning) {
            notices.show(now.wallClock.minus(next.elapsedAt(now.elapsedRealtime)))
        } else {
            notices.cancel()
        }
        next
    }
}
