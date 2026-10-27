package dev.upyet.stopwatch.data

import com.google.common.truth.Truth.assertThat
import dev.upyet.core.time.TimerClock
import dev.upyet.stopwatch.domain.BootCount
import dev.upyet.stopwatch.domain.Stopwatch
import dev.upyet.stopwatch.domain.StopwatchNotices
import dev.upyet.stopwatch.domain.StopwatchStore
import dev.upyet.timer.domain.TimerAnchor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration
import java.time.Instant

class StopwatchControllerTest {
    private val wall0: Instant = Instant.parse("2026-01-01T00:00:00Z")

    private class InMemoryStore(initial: Stopwatch = Stopwatch.IDLE) : StopwatchStore {
        private val state = MutableStateFlow(initial)
        override val stopwatch: StateFlow<Stopwatch> get() = state

        override suspend fun update(transform: (Stopwatch) -> Stopwatch): Stopwatch {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    /** Elapsed realtime and wall clock move together, as they do on a device that has not been rebooted. */
    private class FakeTimerClock(var elapsed: Duration, var wall: Instant) : TimerClock {
        override fun now(): TimerAnchor = TimerAnchor(elapsed, wall)

        fun advance(by: Duration) {
            elapsed = elapsed.plus(by)
            wall = wall.plus(by)
        }
    }

    private class FakeBootCount(var value: Int?) : BootCount {
        override fun current(): Int? = value
    }

    private class RecordingNotices : StopwatchNotices {
        val shown = mutableListOf<Instant>()
        var cancels = 0

        override fun show(startedAt: Instant) {
            shown += startedAt
        }

        override fun cancel() {
            cancels++
        }
    }

    private class Harness(
        initialStore: InMemoryStore = InMemoryStore(),
        val clock: FakeTimerClock,
        val boot: FakeBootCount = FakeBootCount(1),
    ) {
        val store: StopwatchStore = initialStore
        val notices = RecordingNotices()
        val controller = StopwatchController(store, clock, boot, notices)
    }

    private fun sec(n: Long): Duration = Duration.ofSeconds(n)

    @Test
    fun startPostsTheNoticeAnchoredToTheStartInstant() = runTest {
        val clock = FakeTimerClock(elapsed = sec(100), wall = wall0)
        val harness = Harness(clock = clock)

        val started = harness.controller.start()

        assertThat(started.isRunning).isTrue()
        assertThat(harness.notices.shown).containsExactly(wall0)
        assertThat(harness.notices.cancels).isEqualTo(0)
    }

    @Test
    fun pausingCancelsTheNoticeAndStoresTheRunSoFar() = runTest {
        val clock = FakeTimerClock(elapsed = sec(100), wall = wall0)
        val harness = Harness(clock = clock)
        harness.controller.start()

        clock.advance(sec(30))
        val paused = harness.controller.pause()

        assertThat(harness.notices.cancels).isEqualTo(1)
        assertThat(paused.isRunning).isFalse()
        assertThat(paused.accumulated).isEqualTo(sec(30))
        assertThat(harness.store.stopwatch.first().accumulated).isEqualTo(sec(30))
    }

    @Test
    fun resumingAfterAPauseAnchorsTheNoticeSoItCountsFromTheAccumulatedTime() = runTest {
        val clock = FakeTimerClock(elapsed = sec(100), wall = wall0)
        val harness = Harness(clock = clock)
        harness.controller.start()
        clock.advance(sec(30))
        harness.controller.pause()

        clock.advance(sec(500))
        val resumeWall = clock.wall
        harness.controller.start()

        assertThat(harness.notices.shown.last()).isEqualTo(resumeWall.minus(sec(30)))
    }

    @Test
    fun lapKeepsTheNoticeShownAtTheSameStartInstant() = runTest {
        val clock = FakeTimerClock(elapsed = sec(100), wall = wall0)
        val harness = Harness(clock = clock)
        harness.controller.start()

        clock.advance(sec(12))
        val lapped = harness.controller.lap()

        assertThat(lapped.laps).containsExactly(sec(12))
        assertThat(harness.notices.cancels).isEqualTo(0)
        assertThat(harness.notices.shown).containsExactly(wall0, wall0).inOrder()
    }

    @Test
    fun resetCancelsTheNoticeAndStoresIdle() = runTest {
        val clock = FakeTimerClock(elapsed = sec(100), wall = wall0)
        val harness = Harness(clock = clock)
        harness.controller.start()
        clock.advance(sec(5))

        val reset = harness.controller.reset()

        assertThat(reset).isEqualTo(Stopwatch.IDLE)
        assertThat(harness.notices.cancels).isEqualTo(1)
        assertThat(harness.store.stopwatch.first()).isEqualTo(Stopwatch.IDLE)
    }

    @Test
    fun syncResetsARunningStopwatchFromAnEarlierBootAndCancelsTheNotice() = runTest {
        val running = Stopwatch(runningSince = sec(50), bootCount = 1)
        val clock = FakeTimerClock(elapsed = sec(100), wall = wall0)
        val harness = Harness(initialStore = InMemoryStore(running), clock = clock, boot = FakeBootCount(2))

        val synced = harness.controller.sync()

        assertThat(synced).isEqualTo(Stopwatch.IDLE)
        assertThat(harness.store.stopwatch.first()).isEqualTo(Stopwatch.IDLE)
        assertThat(harness.notices.cancels).isEqualTo(1)
        assertThat(harness.notices.shown).isEmpty()
    }

    @Test
    fun syncOnARunningStopwatchFromTheSameBootRepostsTheNoticeIdempotently() = runTest {
        val running = Stopwatch(runningSince = sec(50), bootCount = 1)
        val clock = FakeTimerClock(elapsed = sec(100), wall = wall0)
        val harness = Harness(initialStore = InMemoryStore(running), clock = clock)

        harness.controller.sync()
        harness.controller.sync()

        val expectedStart = wall0.minus(sec(50))
        assertThat(harness.notices.shown).containsExactly(expectedStart, expectedStart).inOrder()
        assertThat(harness.notices.cancels).isEqualTo(0)
        assertThat(harness.store.stopwatch.first()).isEqualTo(running)
    }
}
