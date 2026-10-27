package dev.upyet.timer.ringing

import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.core.directboot.UserUnlockState
import dev.upyet.evidence.domain.OccurrenceOutcome
import dev.upyet.evidence.domain.OccurrenceRepository
import dev.upyet.evidence.domain.TimerAttribution
import dev.upyet.testing.FakeUserUnlockState
import dev.upyet.testing.FixedTimeProvider
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.playback.RingingTimer
import dev.upyet.timer.playback.TimerRingingRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

/** One showing of the timer ringing screen owns at most one timer occurrence (AGENTS.md §2 invariants 11 and 13, ADR 0010). */
class TimerShowingOccurrenceTest {
    private val now = Instant.parse("2026-10-26T12:00:00Z")
    private val repository = FakeOccurrenceRepository()
    private val registry = TimerRingingRegistry()
    private val unlocked = FakeUserUnlockState(unlocked = true)
    private val showing = TimerShowingOccurrence(Lazy<OccurrenceRepository> { repository }, unlocked, FixedTimeProvider(now), registry)

    @Test fun aShowingWhereEveryTimerRecordsOffWritesNothingAndStaysNone() = runTest {
        showing.onRinging(listOf(ringing(1, evidence = false)))
        showing.onRinging(listOf(ringing(1, evidence = false), ringing(2, evidence = false)))
        showing.onRinging(emptyList())

        assertThat(showing.state.value).isEqualTo(ShowingOccurrence.None)
        assertThat(repository.created).isEmpty()
        assertThat(repository.completed).isEmpty()
    }

    @Test fun theFirstRecordingOnRingCreatesOneOccurrenceScheduledAtTheEarliestRecordingRing() = runTest {
        showing.onRinging(
            listOf(ringing(1, evidence = true, since = now.minusSeconds(40)), ringing(2, evidence = true, since = now.minusSeconds(10))),
        )

        assertThat(repository.created).hasSize(1)
        val created = repository.created.single()
        assertThat(
            created.timers,
        ).containsExactly(TimerAttribution(TimerId(1), "Timer 1"), TimerAttribution(TimerId(2), "Timer 2")).inOrder()
        assertThat(created.scheduledFor).isEqualTo(now.minusSeconds(40))
        assertThat(created.triggeredAt).isEqualTo(now.minusSeconds(40))
        assertThat(showing.state.value).isEqualTo(ShowingOccurrence.Open(OccurrenceId(1)))
    }

    @Test fun openingAShowingFirstInterruptsTimerOccurrencesADeadProcessLeftRinging() = runTest {
        showing.onRinging(listOf(ringing(1, evidence = true)))

        assertThat(repository.calls).containsExactly("sweep", "create").inOrder()
    }

    @Test fun aFailedSweepStillOpensTheOccurrence() = runTest {
        repository.sweepFailure = IllegalStateException("disk")

        showing.onRinging(listOf(ringing(1, evidence = true)))

        assertThat(showing.state.value).isEqualTo(ShowingOccurrence.Open(OccurrenceId(1)))
    }

    @Test fun joiningAndClosingNeverSweepTheOpenOccurrence() = runTest {
        showing.onRinging(listOf(ringing(1, evidence = true)))
        showing.onRinging(listOf(ringing(1, evidence = true), ringing(2, evidence = true)))
        showing.onRinging(emptyList())

        assertThat(repository.sweeps).isEqualTo(1)
    }

    @Test fun aRecordingOnTimerJoiningUpdatesTheAttributionOnceAndCreatesNothingMore() = runTest {
        showing.onRinging(listOf(ringing(1, evidence = true)))

        showing.onRinging(listOf(ringing(1, evidence = true), ringing(2, evidence = true)))
        showing.onRinging(listOf(ringing(1, evidence = true), ringing(2, evidence = true)))

        assertThat(repository.created).hasSize(1)
        assertThat(repository.updated).containsExactly(
            OccurrenceId(1) to listOf(TimerAttribution(TimerId(1), "Timer 1"), TimerAttribution(TimerId(2), "Timer 2")),
        ).inOrder()
    }

    @Test fun aRecordingOffTimerJoiningChangesNothing() = runTest {
        showing.onRinging(listOf(ringing(1, evidence = true)))

        showing.onRinging(listOf(ringing(1, evidence = true), ringing(2, evidence = false)))

        assertThat(repository.updated).isEmpty()
        assertThat(repository.completed).isEmpty()
        assertThat(showing.state.value).isEqualTo(ShowingOccurrence.Open(OccurrenceId(1)))
    }

    /** Invariant 13: recording ends with the last recording-on timer, but the occurrence spans the whole showing. */
    @Test fun theLastRecordingOnTimerLeavingWhileRecordingOffTimersRingKeepsTheOccurrenceOpen() = runTest {
        showing.onRinging(listOf(ringing(1, evidence = true), ringing(2, evidence = false)))

        showing.onRinging(listOf(ringing(2, evidence = false)))

        assertThat(repository.completed).isEmpty()
        assertThat(showing.state.value).isEqualTo(ShowingOccurrence.Open(OccurrenceId(1)))
    }

    @Test fun aRecordingOnTimerRingingAgainWhileTheShowingContinuesJoinsTheSameOccurrence() = runTest {
        showing.onRinging(listOf(ringing(1, evidence = true)))
        showing.onRinging(listOf(ringing(2, evidence = false)))

        showing.onRinging(listOf(ringing(2, evidence = false), ringing(1, evidence = true, since = now.minusSeconds(5))))

        assertThat(repository.created).hasSize(1)
        assertThat(repository.updated).isEmpty()
        assertThat(repository.completed).isEmpty()
        assertThat(showing.state.value).isEqualTo(ShowingOccurrence.Open(OccurrenceId(1)))
    }

    /**
     * A screen relaunched into a showing that is still up gets the same showing number, so it joins the occurrence the
     * first screen opened instead of creating a second one (ADR 0010: one showing, one occurrence).
     */
    @Test fun aRelaunchedScreenRejoinsTheSameShowing() = runTest {
        registry.add(ringing(1, evidence = true))
        showing.onRinging(registry.ringing.value)

        registry.add(ringing(2, evidence = true))
        showing.onRinging(registry.ringing.value)
        // A second consumer of the same showing emits the same list again.
        showing.onRinging(registry.ringing.value)

        assertThat(repository.created).hasSize(1)
        assertThat(repository.completed).isEmpty()
        assertThat(repository.updated).containsExactly(
            OccurrenceId(1) to listOf(TimerAttribution(TimerId(1), "Timer 1"), TimerAttribution(TimerId(2), "Timer 2")),
        ).inOrder()
        assertThat(showing.state.value).isEqualTo(ShowingOccurrence.Open(OccurrenceId(1)))
    }

    /**
     * The showing ended and a new one began while no screen was collecting, so the old occurrence never got its Stop.
     * It is interrupted, with no user-ended time, and the new showing gets its own occurrence.
     */
    @Test fun aShowingThatEndedWhileNoScreenWasUpIsInterruptedNotStopped() = runTest {
        registry.add(ringing(1, evidence = true))
        showing.onRinging(registry.ringing.value)

        registry.remove(TimerId(1))
        registry.add(ringing(2, evidence = true))
        showing.onRinging(registry.ringing.value)

        assertThat(repository.completed).containsExactly(
            FakeOccurrenceRepository.Completed(OccurrenceId(1), null, OccurrenceOutcome.INTERRUPTED),
        )
        assertThat(repository.created.map { it.id }).containsExactly(OccurrenceId(1), OccurrenceId(2)).inOrder()
        assertThat(repository.created.last().timers).containsExactly(TimerAttribution(TimerId(2), "Timer 2"))
        assertThat(showing.state.value).isEqualTo(ShowingOccurrence.Open(OccurrenceId(2)))
    }

    /** A locked showing that ended must not hold Unavailable over the next showing, which may open once unlocked. */
    @Test fun anUnavailableShowingThatEndedIsForgottenBeforeTheNextShowingOpens() = runTest {
        val lockedState = object : UserUnlockState {
            var unlockedNow = false

            override fun isUserUnlocked(): Boolean = unlockedNow
        }
        val locked = TimerShowingOccurrence(Lazy<OccurrenceRepository> { repository }, lockedState, FixedTimeProvider(now), registry)
        registry.add(ringing(1, evidence = true))
        locked.onRinging(registry.ringing.value)
        assertThat(locked.state.value).isEqualTo(ShowingOccurrence.Unavailable)

        registry.remove(TimerId(1))
        lockedState.unlockedNow = true
        registry.add(ringing(2, evidence = true))
        locked.onRinging(registry.ringing.value)

        assertThat(repository.created.map { it.id }).containsExactly(OccurrenceId(1))
        assertThat(locked.state.value).isEqualTo(ShowingOccurrence.Open(OccurrenceId(1)))
    }

    @Test fun anEmptyShowingCompletesTheOccurrenceOnceAsStoppedAndReturnsToNone() = runTest {
        showing.onRinging(listOf(ringing(1, evidence = true)))

        showing.onRinging(emptyList())
        showing.onRinging(emptyList())

        assertThat(
            repository.completed,
        ).containsExactly(FakeOccurrenceRepository.Completed(OccurrenceId(1), now, OccurrenceOutcome.STOPPED))
        assertThat(showing.state.value).isEqualTo(ShowingOccurrence.None)
    }

    @Test fun aRingAfterTheShowingEndedIsANewShowingAndANewOccurrence() = runTest {
        showing.onRinging(listOf(ringing(1, evidence = true)))
        showing.onRinging(emptyList())

        showing.onRinging(listOf(ringing(1, evidence = true, since = now.minusSeconds(3))))

        assertThat(repository.created.map { it.id }).containsExactly(OccurrenceId(1), OccurrenceId(2)).inOrder()
        assertThat(showing.state.value).isEqualTo(ShowingOccurrence.Open(OccurrenceId(2)))
    }

    @Test fun beforeFirstUnlockTheShowingIsUnavailableAndRoomIsNeverTouched() = runTest {
        val locked = TimerShowingOccurrence(
            Lazy<OccurrenceRepository> { error("Room must not be built before first unlock") },
            FakeUserUnlockState(unlocked = false),
            FixedTimeProvider(now),
            TimerRingingRegistry(),
        )

        locked.onRinging(listOf(ringing(1, evidence = true)))
        assertThat(locked.state.value).isEqualTo(ShowingOccurrence.Unavailable)

        locked.onRinging(emptyList())
        assertThat(locked.state.value).isEqualTo(ShowingOccurrence.None)
    }

    @Test fun aFailedCreateIsUnavailableAndAnEmptyShowingThenGoesNoneWithoutCompleting() = runTest {
        repository.createFailure = IllegalStateException("disk full")

        showing.onRinging(listOf(ringing(1, evidence = true)))
        assertThat(showing.state.value).isEqualTo(ShowingOccurrence.Unavailable)

        showing.onRinging(emptyList())

        assertThat(showing.state.value).isEqualTo(ShowingOccurrence.None)
        assertThat(repository.completed).isEmpty()
    }

    /** A failed attribution write is retried by the next emission, because the timer is still unattributed. */
    @Test fun aFailedAttributionWriteIsRetriedOnTheNextEmission() = runTest {
        showing.onRinging(listOf(ringing(1, evidence = true)))
        repository.updateFailure = IllegalStateException("disk full")
        showing.onRinging(listOf(ringing(1, evidence = true), ringing(2, evidence = true)))
        assertThat(repository.updated).isEmpty()

        repository.updateFailure = null
        showing.onRinging(listOf(ringing(1, evidence = true), ringing(2, evidence = true)))

        assertThat(repository.updated).containsExactly(
            OccurrenceId(1) to listOf(TimerAttribution(TimerId(1), "Timer 1"), TimerAttribution(TimerId(2), "Timer 2")),
        ).inOrder()
    }

    /** Cancellation is not a write failure: it must reach the collector so the ViewModel's scope can end. */
    @Test fun cancellationIsRethrownAndNotSwallowedAsAFailedWrite() = runTest {
        repository.createFailure = CancellationException("scope cancelled")
        var rethrown = false

        try {
            showing.onRinging(listOf(ringing(1, evidence = true)))
        } catch (cancelled: CancellationException) {
            rethrown = true
        }

        assertThat(rethrown).isTrue()
    }

    private fun ringing(id: Long, evidence: Boolean, since: Instant = now.minusSeconds(100 - id)) = RingingTimer(
        TimerId(id),
        label = "Timer $id",
        length = null,
        ringtoneUri = null,
        evidenceEnabled = evidence,
        ringingSince = since,
    )
}
