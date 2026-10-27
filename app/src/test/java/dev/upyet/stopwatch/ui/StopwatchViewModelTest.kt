package dev.upyet.stopwatch.ui

import com.google.common.truth.Truth.assertThat
import dev.upyet.core.time.TimerClock
import dev.upyet.stopwatch.data.StopwatchController
import dev.upyet.stopwatch.domain.BootCount
import dev.upyet.stopwatch.domain.LapRow
import dev.upyet.stopwatch.domain.Stopwatch
import dev.upyet.stopwatch.domain.StopwatchNotices
import dev.upyet.stopwatch.domain.StopwatchStore
import dev.upyet.timer.domain.TimerAnchor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StopwatchViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun startsIdle() = runTest {
        val h = Harness()
        val observer = launch { h.viewModel.state.collect {} }

        runCurrent()

        assertThat(h.viewModel.state.value).isEqualTo(StopwatchUiState())
        observer.cancel()
    }

    @Test fun startMovesToRunning() = runTest {
        val h = Harness()
        val observer = launch { h.viewModel.state.collect {} }

        h.viewModel.onStart()
        runCurrent()

        assertThat(h.viewModel.state.value.stopwatch.isRunning).isTrue()
        assertThat(h.notices.shown).hasSize(1)
        observer.cancel()
    }

    @Test fun lapsAreExposedOldestFirstWithTheirSplits() = runTest {
        val h = Harness()
        val observer = launch { h.viewModel.state.collect {} }

        h.viewModel.onStart()
        h.clock.anchor = at(10)
        h.viewModel.onLap()
        h.clock.anchor = at(25)
        h.viewModel.onLap()
        runCurrent()

        assertThat(h.viewModel.state.value.laps).containsExactly(
            LapRow(number = 1, split = Duration.ofSeconds(10), total = Duration.ofSeconds(10)),
            LapRow(number = 2, split = Duration.ofSeconds(15), total = Duration.ofSeconds(25)),
        ).inOrder()
        observer.cancel()
    }

    @Test fun pauseThenResetReturnsToIdle() = runTest {
        val h = Harness()
        val observer = launch { h.viewModel.state.collect {} }

        h.viewModel.onStart()
        h.clock.anchor = at(5)
        h.viewModel.onPause()
        runCurrent()
        assertThat(h.viewModel.state.value.stopwatch.isRunning).isFalse()
        assertThat(h.viewModel.state.value.stopwatch.accumulated).isEqualTo(Duration.ofSeconds(5))

        h.viewModel.onReset()
        runCurrent()
        assertThat(h.viewModel.state.value.stopwatch.isIdle).isTrue()
        observer.cancel()
    }

    @Test fun lapIsIgnoredWhileIdle() = runTest {
        val h = Harness()
        val observer = launch { h.viewModel.state.collect {} }

        h.viewModel.onLap()
        runCurrent()

        assertThat(h.viewModel.state.value.laps).isEmpty()
        observer.cancel()
    }

    @Test fun initSyncResetsAStopwatchStoredFromAnEarlierBoot() = runTest {
        val stored = Stopwatch(accumulated = Duration.ofSeconds(5), bootCount = 0, laps = listOf(Duration.ofSeconds(5)))
        val h = Harness(initial = stored, boot = 1)

        // Construction runs the init sync; nothing else is called.
        runCurrent()

        assertThat(h.store.state.value).isEqualTo(Stopwatch.IDLE)
        assertThat(h.notices.cancelled).isEqualTo(1)
    }

    @Test fun initSyncKeepsAStopwatchFromTheSameBoot() = runTest {
        val stored = Stopwatch(accumulated = Duration.ofSeconds(5), bootCount = 1)
        val h = Harness(initial = stored, boot = 1)

        runCurrent()

        assertThat(h.store.state.value).isEqualTo(stored)
    }

    private fun at(seconds: Long) = TimerAnchor(Duration.ofSeconds(seconds), Instant.EPOCH.plusSeconds(seconds))

    private inner class Harness(initial: Stopwatch = Stopwatch.IDLE, boot: Int? = 1) {
        val clock = MutableClock(at(0))
        val store = MemoryStore(initial)
        val notices = RecordingNotices()
        val controller = StopwatchController(store, clock, BootCount { boot }, notices)
        val viewModel = StopwatchViewModel(controller)
    }

    private class MutableClock(var anchor: TimerAnchor) : TimerClock {
        override fun now(): TimerAnchor = anchor
    }

    private class MemoryStore(initial: Stopwatch) : StopwatchStore {
        val state = MutableStateFlow(initial)
        override val stopwatch: Flow<Stopwatch> get() = state

        override suspend fun update(transform: (Stopwatch) -> Stopwatch): Stopwatch {
            state.update(transform)
            return state.value
        }
    }

    private class RecordingNotices : StopwatchNotices {
        val shown = mutableListOf<Instant>()
        var cancelled = 0

        override fun show(startedAt: Instant) {
            shown += startedAt
        }

        override fun cancel() {
            cancelled++
        }
    }
}
