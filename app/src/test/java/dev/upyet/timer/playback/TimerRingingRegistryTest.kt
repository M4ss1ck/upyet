package dev.upyet.timer.playback

import com.google.common.truth.Truth.assertThat
import dev.upyet.timer.domain.TimerId
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

/** The showing number names one showing of the timer ringing screen (AGENTS.md §2 invariant 11, ADR 0010). */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerRingingRegistryTest {
    private val registry = TimerRingingRegistry()

    @Test fun theFirstRingOfAShowingStartsShowingOne() {
        assertThat(registry.showing.value).isEqualTo(0L)

        registry.add(ringing(1))

        assertThat(registry.showing.value).isEqualTo(1L)
    }

    @Test fun aSecondTimerRingingWhileTheShowingIsUpKeepsItsNumber() {
        registry.add(ringing(1))

        registry.add(ringing(2))

        assertThat(registry.showing.value).isEqualTo(1L)
    }

    @Test fun aTimerLeavingWhileOthersRingKeepsTheNumber() {
        registry.add(ringing(1))
        registry.add(ringing(2))

        registry.remove(TimerId(1))

        assertThat(registry.showing.value).isEqualTo(1L)
    }

    @Test fun aRingAfterTheListEmptiedIsANewShowing() {
        registry.add(ringing(1))
        registry.remove(TimerId(1))
        assertThat(registry.showing.value).isEqualTo(1L)

        registry.add(ringing(1))

        assertThat(registry.showing.value).isEqualTo(2L)
    }

    @Test fun aClearedRegistryStartsANewShowingOnTheNextRing() {
        registry.add(ringing(1))
        registry.add(ringing(2))

        registry.clear()
        registry.add(ringing(3))

        assertThat(registry.showing.value).isEqualTo(2L)
    }

    @Test fun aRepeatedAddOfATimerAlreadyRingingChangesNothing() {
        registry.add(ringing(1))

        registry.add(ringing(1))

        assertThat(registry.showing.value).isEqualTo(1L)
        assertThat(registry.ringing.value.map { it.id }).containsExactly(TimerId(1))
    }

    /**
     * A collector of [TimerRingingRegistry.ringing] that wakes on the first ring must already see the new number, so it
     * never pairs a new showing's timers with the old number.
     */
    @Test fun theNumberIsPublishedBeforeTheListThatStartsTheShowing() = runTest {
        val seen = mutableListOf<Pair<Int, Long>>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            registry.ringing.collect { list -> seen += list.size to registry.showing.value }
        }

        registry.add(ringing(1))
        collector.cancel()

        assertThat(seen).containsExactly(0 to 0L, 1 to 1L).inOrder()
    }

    private fun ringing(id: Long) = RingingTimer(
        id = TimerId(id),
        label = "Timer $id",
        length = null,
        ringtoneUri = null,
        evidenceEnabled = false,
        ringingSince = Instant.parse("2026-10-26T12:00:00Z"),
    )
}
