package dev.upyet.timer.ringing

import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.core.directboot.PendingOccurrences
import dev.upyet.core.directboot.PendingRecord
import dev.upyet.core.directboot.PendingTimerOccurrence
import dev.upyet.core.directboot.UserUnlockState
import dev.upyet.evidence.domain.AlarmOccurrence
import dev.upyet.evidence.domain.EvidenceErrorCode
import dev.upyet.evidence.domain.OccurrenceOutcome
import dev.upyet.evidence.domain.OccurrenceRepository
import dev.upyet.evidence.domain.OccurrenceSource
import dev.upyet.evidence.domain.TimerAttribution
import dev.upyet.testing.FakePendingOccurrences
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
    private val pending = FakePendingOccurrences()
    private val showing =
        TimerShowingOccurrence(
            Lazy<OccurrenceRepository> {
                repository
            },
            unlocked,
            FixedTimeProvider(now),
            registry,
            pending,
        )

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
        val locked =
            TimerShowingOccurrence(
                Lazy<OccurrenceRepository> {
                    repository
                },
                lockedState,
                FixedTimeProvider(now),
                registry,
                FakePendingOccurrences(),
            )
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
            FakePendingOccurrences(),
        )

        locked.onRinging(listOf(ringing(1, evidence = true)))
        assertThat(locked.state.value).isEqualTo(ShowingOccurrence.Unavailable)

        locked.onRinging(emptyList())
        assertThat(locked.state.value).isEqualTo(ShowingOccurrence.None)
    }

    /** Before first unlock the showing's occurrence waits in device-protected storage as a timer record (ADR 0010). */
    @Test fun aRecordingOnRingBeforeFirstUnlockAppendsOnePendingTimerOccurrence() = runTest {
        val locked = lockedShowing(pending)

        locked.onRinging(listOf(ringing(1, evidence = true)))

        val record = pending.all().single()
        assertThat(record.source).isEqualTo(OccurrenceSource.TIMER)
        assertThat(record).isEqualTo(
            PendingTimerOccurrence(
                timers = listOf(TimerAttribution(TimerId(1), "Timer 1")),
                triggeredAt = now.minusSeconds(99),
                dismissedAt = null,
                outcome = OccurrenceOutcome.RINGING.name,
                evidenceError = EvidenceErrorCode.DIRECT_BOOT_UNAVAILABLE,
            ),
        )
        assertThat(locked.state.value).isEqualTo(ShowingOccurrence.Unavailable)
        assertThat(roomBuilds).isEqualTo(0)
        assertThat(repository.created).isEmpty()
        assertThat(repository.calls).isEmpty()
    }

    @Test fun aRecordingOffOnlyShowingBeforeFirstUnlockWritesNoPendingRecord() = runTest {
        val locked = lockedShowing(pending)

        locked.onRinging(listOf(ringing(1, evidence = false)))
        locked.onRinging(emptyList())

        assertThat(pending.all()).isEmpty()
        assertThat(locked.state.value).isEqualTo(ShowingOccurrence.None)
        assertThat(roomBuilds).isEqualTo(0)
    }

    /** A joining timer is added to the one pending record; a timer ringing again keeps its first position and is not repeated. */
    @Test fun aRecordingOnTimerJoiningBeforeFirstUnlockIsAppendedToTheSameRecordOnce() = runTest {
        val locked = lockedShowing(pending)
        locked.onRinging(listOf(ringing(1, evidence = true)))

        locked.onRinging(listOf(ringing(1, evidence = true), ringing(2, evidence = true)))
        locked.onRinging(listOf(ringing(2, evidence = true, since = now.minusSeconds(5)), ringing(1, evidence = true)))

        assertThat(pending.all()).containsExactly(
            PendingTimerOccurrence(
                timers = listOf(TimerAttribution(TimerId(1), "Timer 1"), TimerAttribution(TimerId(2), "Timer 2")),
                triggeredAt = now.minusSeconds(99),
                dismissedAt = null,
                outcome = OccurrenceOutcome.RINGING.name,
            ),
        )
        assertThat(locked.state.value).isEqualTo(ShowingOccurrence.Unavailable)
        assertThat(roomBuilds).isEqualTo(0)
    }

    @Test fun aLockedShowingThatEndsUpdatesItsPendingRecordToStoppedOnce() = runTest {
        val locked = lockedShowing(pending)
        locked.onRinging(listOf(ringing(1, evidence = true)))

        locked.onRinging(emptyList())
        locked.onRinging(emptyList())

        assertThat(pending.all()).containsExactly(
            PendingTimerOccurrence(
                timers = listOf(TimerAttribution(TimerId(1), "Timer 1")),
                triggeredAt = now.minusSeconds(99),
                dismissedAt = now,
                outcome = OccurrenceOutcome.STOPPED.name,
            ),
        )
        assertThat(locked.state.value).isEqualTo(ShowingOccurrence.None)
        assertThat(roomBuilds).isEqualTo(0)
    }

    /** The showing number moved on with no empty list, so the old showing ended unseen: interrupted, no user-ended time. */
    @Test fun aLockedShowingWhoseNumberMovedOnIsRecordedAsInterruptedWithNoUserEndedTime() = runTest {
        val lockedRegistry = TimerRingingRegistry()
        val locked = lockedShowing(pending, lockedRegistry)
        lockedRegistry.add(ringing(1, evidence = true))
        locked.onRinging(lockedRegistry.ringing.value)

        lockedRegistry.remove(TimerId(1))
        lockedRegistry.add(ringing(2, evidence = false))
        locked.onRinging(lockedRegistry.ringing.value)

        assertThat(pending.all()).containsExactly(
            PendingTimerOccurrence(
                timers = listOf(TimerAttribution(TimerId(1), "Timer 1")),
                triggeredAt = now.minusSeconds(99),
                dismissedAt = null,
                outcome = OccurrenceOutcome.INTERRUPTED.name,
            ),
        )
        assertThat(locked.state.value).isEqualTo(ShowingOccurrence.None)
    }

    /** USER_UNLOCKED reconciled the record into Room and cleared the store: the showing must not write it back. */
    @Test fun aLockedShowingWhosePendingRecordWasClearedWritesNothingBackWhenItEnds() = runTest {
        val locked = lockedShowing(pending)
        locked.onRinging(listOf(ringing(1, evidence = true)))
        pending.clear()

        locked.onRinging(emptyList())

        assertThat(pending.cleared).isEqualTo(1)
        assertThat(pending.all()).isEmpty()
        assertThat(locked.state.value).isEqualTo(ShowingOccurrence.None)
    }

    /**
     * The user unlocked while the showing still rang, so `USER_UNLOCKED` turned the record into a ringing Room row and
     * cleared the store. Joining and stopping now reach that row, never a second pending record.
     */
    @Test fun aShowingReconciledWhileRingingHandsItsJoinAndStopToTheReconciledRow() = runTest {
        val lockState = object : UserUnlockState {
            var unlockedNow = false

            override fun isUserUnlocked(): Boolean = unlockedNow
        }
        val since = now.minusSeconds(30)
        val handedOver =
            TimerShowingOccurrence(Lazy<OccurrenceRepository> { repository }, lockState, FixedTimeProvider(now), registry, pending)
        handedOver.onRinging(listOf(ringing(1, evidence = true, since = since)))
        pending.clear()
        lockState.unlockedNow = true
        repository.rows = listOf(
            reconciledRow(OccurrenceId(9), since.minusSeconds(3600), OccurrenceOutcome.STOPPED),
            reconciledRow(OccurrenceId(8), since, OccurrenceOutcome.RINGING),
        )

        handedOver.onRinging(listOf(ringing(1, evidence = true, since = since), ringing(2, evidence = true)))
        handedOver.onRinging(emptyList())

        assertThat(pending.all()).isEmpty()
        assertThat(repository.updated)
            .containsExactly(OccurrenceId(8) to listOf(TimerAttribution(TimerId(1), "Timer 1"), TimerAttribution(TimerId(2), "Timer 2")))
        assertThat(
            repository.completed,
        ).containsExactly(FakeOccurrenceRepository.Completed(OccurrenceId(8), now, OccurrenceOutcome.STOPPED))
        assertThat(handedOver.state.value).isEqualTo(ShowingOccurrence.None)
    }

    /** Still locked when the record went missing (its write failed): Room is not built and nothing is completed. */
    @Test fun aMissingPendingRecordWhileStillLockedNeverReachesRoom() = runTest {
        val locked = lockedShowing(pending)
        locked.onRinging(listOf(ringing(1, evidence = true)))
        pending.clear()

        locked.onRinging(emptyList())

        assertThat(roomBuilds).isEqualTo(0)
        assertThat(locked.state.value).isEqualTo(ShowingOccurrence.None)
    }

    private fun reconciledRow(id: OccurrenceId, triggeredAt: Instant, outcome: OccurrenceOutcome) = AlarmOccurrence(
        id = id,
        alarmId = null,
        scheduledFor = triggeredAt,
        triggeredAt = triggeredAt,
        activityVisibleAt = null,
        dismissedAt = null,
        outcome = outcome,
        parentOccurrenceId = null,
        source = OccurrenceSource.TIMER,
        timers = listOf(TimerAttribution(TimerId(1), "Timer 1")),
    )

    @Test fun aFailedUnlockedCreateWritesNoPendingRecordAndEndingItWritesNothing() = runTest {
        repository.createFailure = IllegalStateException("disk full")

        showing.onRinging(listOf(ringing(1, evidence = true)))
        assertThat(showing.state.value).isEqualTo(ShowingOccurrence.Unavailable)
        assertThat(pending.all()).isEmpty()

        showing.onRinging(emptyList())

        assertThat(pending.all()).isEmpty()
        assertThat(repository.completed).isEmpty()
        assertThat(showing.state.value).isEqualTo(ShowingOccurrence.None)
    }

    /** A pending write that throws is logged, never thrown: the ring still goes on and the showing still ends. */
    @Test fun aPendingWriteThatThrowsLeavesTheShowingUnavailableAndDoesNotThrow() = runTest {
        val throwing = object : PendingOccurrences {
            override fun append(record: PendingRecord): Unit = throw IllegalStateException("prefs corrupt")

            override fun replaceIfPending(record: PendingRecord): Boolean = false

            override fun all(): List<PendingRecord> = emptyList()

            override fun clear() = Unit
        }
        val locked = lockedShowing(throwing)

        locked.onRinging(listOf(ringing(1, evidence = true)))
        assertThat(locked.state.value).isEqualTo(ShowingOccurrence.Unavailable)

        locked.onRinging(emptyList())
        assertThat(locked.state.value).isEqualTo(ShowingOccurrence.None)
        assertThat(roomBuilds).isEqualTo(0)
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

    private var roomBuilds = 0

    /**
     * A showing that is locked for good. Every attempt to build Room is counted, then fails. The failure alone would be
     * swallowed as a failed write, so [roomBuilds] is what proves Room was never touched.
     */
    private fun lockedShowing(pending: PendingOccurrences, registry: TimerRingingRegistry = TimerRingingRegistry()) =
        TimerShowingOccurrence(
            Lazy<OccurrenceRepository> {
                roomBuilds++
                error("Room must not be built before first unlock")
            },
            FakeUserUnlockState(unlocked = false),
            FixedTimeProvider(now),
            registry,
            pending,
        )

    private fun ringing(id: Long, evidence: Boolean, since: Instant = now.minusSeconds(100 - id)) = RingingTimer(
        TimerId(id),
        label = "Timer $id",
        length = null,
        ringtoneUri = null,
        evidenceEnabled = evidence,
        ringingSince = since,
    )
}
