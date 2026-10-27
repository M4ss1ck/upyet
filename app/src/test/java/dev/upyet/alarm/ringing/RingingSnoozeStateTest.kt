package dev.upyet.alarm.ringing

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.SnoozeBudget
import dev.upyet.alarm.domain.SnoozePolicy
import dev.upyet.alarm.playback.RingingSession
import dev.upyet.alarm.scheduling.AlarmOccurrenceKind
import org.junit.Test
import java.time.Instant

class RingingSnoozeStateTest {
    private val now = Instant.parse("2026-10-27T07:00:00Z")

    private fun session(snooze: SnoozePolicy, budget: SnoozeBudget) = RingingSession(
        alarmId = AlarmId(1),
        occurrenceId = null,
        scheduledFor = now,
        triggeredAt = now,
        label = null,
        kind = AlarmOccurrenceKind.MAIN,
        snooze = snooze,
        evidenceEnabled = false,
        isUserUnlocked = true,
        budget = budget,
        chainStartedAt = now,
    )

    @Test
    fun offRingNeverAllowsOrOffersSnoozeEvenWithUnlimitedBudget() {
        val ring = session(SnoozePolicy.Off, SnoozeBudget(SnoozeBudget.UNLIMITED))

        assertThat(snoozeAllowedFor(ring)).isFalse()
        assertThat(snoozeOfferedFor(ring)).isFalse()
    }

    @Test
    fun afterRingWithBudgetAllowsAndOffersSnooze() {
        val ring = session(SnoozePolicy.After(9), SnoozeBudget(2))

        assertThat(snoozeAllowedFor(ring)).isTrue()
        assertThat(snoozeOfferedFor(ring)).isTrue()
    }

    @Test
    fun afterRingWithExhaustedBudgetOffersButDisallowsSnooze() {
        val ring = session(SnoozePolicy.After(9), SnoozeBudget(0))

        assertThat(snoozeAllowedFor(ring)).isFalse()
        assertThat(snoozeOfferedFor(ring)).isTrue()
    }

    @Test
    fun noSessionKeepsTheDefaultsAsBefore() {
        assertThat(snoozeAllowedFor(null)).isTrue()
        assertThat(snoozeOfferedFor(null)).isTrue()
    }
}
