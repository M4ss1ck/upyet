package dev.upyet.stopwatch.data

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import dev.upyet.stopwatch.domain.Stopwatch
import dev.upyet.testing.FakeDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration

class DataStoreStopwatchStoreTest {
    private val running = Stopwatch(accumulated = Duration.ofSeconds(3), runningSince = Duration.ofSeconds(10), bootCount = 7)
    private val paused = Stopwatch(
        accumulated = Duration.ofMillis(42_500),
        bootCount = 7,
        laps = listOf(Duration.ofMillis(10_000), Duration.ofMillis(42_500)),
    )

    @Test
    fun runningStopwatchSurvivesAnEncodeDecodeRoundTrip() {
        assertThat(decodeStopwatch(encodeStopwatch(running))).isEqualTo(running)
    }

    @Test
    fun pausedStopwatchWithLapsSurvivesAnEncodeDecodeRoundTrip() {
        assertThat(decodeStopwatch(encodeStopwatch(paused))).isEqualTo(paused)
    }

    @Test
    fun idleStopwatchRoundTripsAndEncodesToNoKeys() {
        assertThat(encodeStopwatch(Stopwatch.IDLE).asMap()).isEmpty()
        assertThat(decodeStopwatch(encodeStopwatch(Stopwatch.IDLE))).isEqualTo(Stopwatch.IDLE)
    }

    @Test
    fun resettingAStopwatchClearsKeysAStoppedRunLeftBehind() {
        assertThat(encodeStopwatch(running).asMap()).isNotEmpty()
        assertThat(encodeStopwatch(Stopwatch.IDLE).asMap()).isEmpty()
    }

    @Test
    fun malformedLapsDecodeToNoLapsAtAll() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("laps_ms") to "10000,abc,30000",
            longPreferencesKey("accumulated_ms") to 5_000L,
        )
        assertThat(decodeStopwatch(prefs).laps).isEmpty()
        assertThat(decodeStopwatch(prefs).accumulated).isEqualTo(Duration.ofSeconds(5))
    }

    @Test
    fun emptyLapsDecodeToNoLaps() {
        assertThat(decodeLaps("")).isEmpty()
        assertThat(decodeLaps(null)).isEmpty()
    }

    @Test
    fun updateReturnsTheTransformedValueAndTheFlowEmitsIt() = runTest {
        val store = DataStoreStopwatchStore(FakeDataStore())

        val returned = store.update { running }

        assertThat(returned).isEqualTo(running)
        assertThat(store.stopwatch.first()).isEqualTo(running)
    }

    @Test
    fun updateReceivesTheStoredValue() = runTest {
        val store = DataStoreStopwatchStore(FakeDataStore())
        store.update { paused }

        val seen = mutableListOf<Stopwatch>()
        val next = store.update { current ->
            seen += current
            current.reset()
        }

        assertThat(seen).containsExactly(paused)
        assertThat(next).isEqualTo(Stopwatch.IDLE)
        assertThat(store.stopwatch.first()).isEqualTo(Stopwatch.IDLE)
    }
}
