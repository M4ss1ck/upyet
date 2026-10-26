package dev.upyet.timer.playback

import com.google.common.truth.Truth.assertThat
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRunState
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant

/**
 * ADR 0008 and AGENTS.md §8a below the service: one sound for every ringing timer, Stop, +1 min and Stop all,
 * against a fake player and a fake store. One event log records both, so the tests can pin the order: the
 * sound always stops before anything is persisted.
 */
class TimerRingingTest {
    private val events = mutableListOf<String>()
    private val registry = TimerRingingRegistry()
    private var now = TimerAnchor(Duration.ofHours(1), Instant.parse("2026-10-26T08:00:00Z"))

    private val store = FakeRingingStore(events)
    private val output = object : TimerRingingOutput {
        override fun start(ringtoneUri: String?) {
            events += "sound:start:$ringtoneUri"
        }

        override fun stop() {
            events += "sound:stop"
        }
    }

    private val ringing = TimerRinging(registry, store, output) { now }

    private fun stored(id: Long, label: String = "Tea", evidence: Boolean = false, ringtone: String? = "content://tone/$id") =
        Timer.restore(TimerId(id), Duration.ofMinutes(5), label, ringtone, evidence, TimerRunState.Ringing, Instant.EPOCH)

    @Test
    fun aRingStartsTheSoundWithThatTimersRingtoneAndListsIt() = runTest {
        store.timers[1] = stored(1, label = "Pasta")

        val result = ringing.ring(TimerId(1))

        assertThat(result).containsExactly(
            RingingTimer(TimerId(1), "Pasta", Duration.ofMinutes(5), "content://tone/1", false, now.wallClock),
        )
        assertThat(registry.ringing.value).isEqualTo(result)
        assertThat(events).containsExactly("sound:start:content://tone/1")
    }

    @Test
    fun aSecondTimerJoinsTheRingingWithoutASecondSound() = runTest {
        store.timers[1] = stored(1)
        store.timers[2] = stored(2)

        ringing.ring(TimerId(1))
        now = now.copy(wallClock = now.wallClock.plusSeconds(30))
        val result = ringing.ring(TimerId(2))

        assertThat(result.map { it.id }).containsExactly(TimerId(1), TimerId(2)).inOrder()
        assertThat(events).containsExactly("sound:start:content://tone/1")
    }

    @Test
    fun aRingForATimerNoLongerStoredAsRingingIsDroppedSilently() = runTest {
        val result = ringing.ring(TimerId(9))

        assertThat(result).isEmpty()
        assertThat(events).isEmpty()
    }

    @Test
    fun aRepeatedRingForTheSameTimerChangesNothing() = runTest {
        store.timers[1] = stored(1)

        ringing.ring(TimerId(1))
        val result = ringing.ring(TimerId(1))

        assertThat(result).hasSize(1)
        assertThat(events).containsExactly("sound:start:content://tone/1")
    }

    @Test
    fun aStoreThatCannotBeReadStillRingsWithDefaultsAndRecordsNothing() = runTest {
        // Invariant 3: a storage failure is a recorded state, never a silent timer. Evidence stays off, so nothing
        // records for a toggle nobody could read, and the details are marked unreadable so no Stop is offered.
        store.failReads = true

        val result = ringing.ring(TimerId(1))

        assertThat(result).containsExactly(RingingTimer(TimerId(1), "", null, null, false, now.wallClock, detailsUnreadable = true))
        assertThat(TimerHeadsUp.of(result)).isEqualTo(TimerHeadsUp.TapToOpen)
        assertThat(events).containsExactly("sound:start:null")
    }

    @Test
    fun aStoreThatDoesNotAnswerWithinASecondDoesNotHoldTheSoundBack() = runTest {
        store.timers[1] = stored(1)
        store.hangReads = true

        val result = ringing.ring(TimerId(1))

        assertThat(result.single().detailsUnreadable).isTrue()
        assertThat(events).containsExactly("sound:start:null")
    }

    @Test
    fun stopTakesThatTimerOutPersistsItAndLeavesTheSoundForTheOthers() = runTest {
        store.timers[1] = stored(1)
        store.timers[2] = stored(2)
        ringing.ring(TimerId(1))
        ringing.ring(TimerId(2))

        val result = ringing.stop(TimerId(1))

        assertThat(result.map { it.id }).containsExactly(TimerId(2))
        assertThat(events).containsExactly("sound:start:content://tone/1", "store:stop:1").inOrder()
    }

    @Test
    fun stoppingTheLastTimerSilencesBeforeAnythingIsPersisted() = runTest {
        store.timers[1] = stored(1)
        ringing.ring(TimerId(1))

        val result = ringing.stop(TimerId(1))

        assertThat(result).isEmpty()
        assertThat(registry.ringing.value).isEmpty()
        assertThat(events).containsExactly("sound:start:content://tone/1", "sound:stop", "store:stop:1").inOrder()
    }

    @Test
    fun stopStillSilencesWhenTheStoreCannotBeWritten() = runTest {
        store.timers[1] = stored(1)
        ringing.ring(TimerId(1))
        store.failWrites = true

        val result = ringing.stop(TimerId(1))

        assertThat(result).isEmpty()
        assertThat(events).containsExactly("sound:start:content://tone/1", "sound:stop").inOrder()
    }

    @Test
    fun addMinuteTakesTheTimerOutOfTheShowingExactlyLikeStopAndRestartsItAtOneMinute() = runTest {
        store.timers[1] = stored(1)
        store.timers[2] = stored(2)
        ringing.ring(TimerId(1))
        ringing.ring(TimerId(2))

        val result = ringing.addMinute(TimerId(2))

        assertThat(result.map { it.id }).containsExactly(TimerId(1))
        assertThat(events).containsExactly("sound:start:content://tone/1", "store:addMinute:2").inOrder()
    }

    @Test
    fun addMinuteOnTheLastTimerSilencesFirst() = runTest {
        store.timers[1] = stored(1)
        ringing.ring(TimerId(1))

        ringing.addMinute(TimerId(1))

        assertThat(events).containsExactly("sound:start:content://tone/1", "sound:stop", "store:addMinute:1").inOrder()
    }

    @Test
    fun stopAllSilencesOnceThenStopsEveryRingingTimer() = runTest {
        store.timers[1] = stored(1)
        store.timers[2] = stored(2)
        store.timers[3] = stored(3)
        ringing.ring(TimerId(1))
        ringing.ring(TimerId(2))
        ringing.ring(TimerId(3))

        val result = ringing.stopAll()

        assertThat(result).isEmpty()
        assertThat(events).containsExactly(
            "sound:start:content://tone/1",
            "sound:stop",
            "store:stop:1",
            "store:stop:2",
            "store:stop:3",
        ).inOrder()
    }

    @Test
    fun oneFailedWriteDoesNotKeepStopAllFromTheRest() = runTest {
        store.timers[1] = stored(1)
        store.timers[2] = stored(2)
        ringing.ring(TimerId(1))
        ringing.ring(TimerId(2))
        store.failWritesFor = setOf(TimerId(1))

        ringing.stopAll()

        assertThat(events).containsExactly("sound:start:content://tone/1", "sound:stop", "store:stop:2").inOrder()
    }

    @Test
    fun stopForATimerThatIsNotInTheShowingStillReachesTheStore() = runTest {
        // A notification action that outlived its process: the timer may still be stored as Ringing.
        val result = ringing.stop(TimerId(4))

        assertThat(result).isEmpty()
        assertThat(events).containsExactly("store:stop:4")
    }

    @Test
    fun aTimerThatRingsAgainAfterTheShowingEndedStartsTheSoundAgain() = runTest {
        store.timers[1] = stored(1)
        ringing.ring(TimerId(1))
        ringing.addMinute(TimerId(1))
        store.timers[1] = stored(1)

        ringing.ring(TimerId(1))

        assertThat(events).containsExactly(
            "sound:start:content://tone/1",
            "sound:stop",
            "store:addMinute:1",
            "sound:start:content://tone/1",
        ).inOrder()
    }

    private class FakeRingingStore(private val events: MutableList<String>) : TimerRingingStore {
        val timers = mutableMapOf<Long, Timer>()
        var failReads = false
        var hangReads = false
        var failWrites = false
        var failWritesFor = emptySet<TimerId>()

        override suspend fun ringing(id: TimerId): Timer? {
            if (failReads) throw IOException("disk")
            if (hangReads) awaitCancellation()
            return timers[id.value]?.takeIf { it.runState == TimerRunState.Ringing }
        }

        override suspend fun stop(id: TimerId) = write("stop", id)

        override suspend fun addMinute(id: TimerId) = write("addMinute", id)

        private fun write(what: String, id: TimerId) {
            if (failWrites || id in failWritesFor) throw IOException("disk")
            timers.remove(id.value)
            events += "store:$what:${id.value}"
        }
    }
}
