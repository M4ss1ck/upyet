package dev.upyet.core.directboot

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.evidence.domain.EvidenceErrorCode
import dev.upyet.evidence.domain.OccurrenceSource
import dev.upyet.evidence.domain.TimerAttribution
import dev.upyet.timer.domain.TimerId
import org.junit.Test
import java.time.Instant

/**
 * The pending occurrence record is a versioned `|` string in device-protected storage, written before first unlock and
 * read back by `USER_UNLOCKED`. Its encoding is a compatibility contract: a legacy alarm record must still decode, or an
 * upgrade drops an in-flight alarm.
 */
class PendingOccurrenceCodecTest {
    private val scheduled = Instant.ofEpochMilli(1767250800000)
    private val triggered = Instant.ofEpochMilli(1767250805000)
    private val dismissed = Instant.ofEpochMilli(1767250900000)

    private fun roundTrip(record: PendingRecord): PendingRecord? = PendingOccurrenceCodec.decode(PendingOccurrenceCodec.encode(record))

    @Test
    fun anAlarmRecordWithADismissTimeRoundTrips() {
        val record = PendingOccurrence(AlarmId(7), scheduled, triggered, dismissed, "DISMISSED")

        assertThat(roundTrip(record)).isEqualTo(record)
    }

    @Test
    fun anAlarmRecordWithoutADismissTimeRoundTrips() {
        val record = PendingOccurrence(AlarmId(7), scheduled, triggered, null, "RINGING")

        assertThat(roundTrip(record)).isEqualTo(record)
    }

    /** Literal written out by hand, so a change to the field order fails here even if encode and decode change together. */
    @Test
    fun anAlarmRecordEncodesToTheVersionTwoLiteral() {
        val record = PendingOccurrence(AlarmId(7), scheduled, triggered, null, "RINGING")

        assertThat(PendingOccurrenceCodec.encode(record)).isEqualTo("2|ALARM|7|1767250800000|1767250805000|-1|RINGING")
    }

    @Test
    fun aTimerRecordRoundTripsWithLabelsThatContainSeparators() {
        val record = PendingTimerOccurrence(
            timers = listOf(
                TimerAttribution(TimerId(3), "Pasta | al dente, 8:00 ñ"),
                TimerAttribution(TimerId(4), "50% off, 100%: done"),
            ),
            triggeredAt = triggered,
            dismissedAt = dismissed,
            outcome = "STOPPED",
            evidenceError = EvidenceErrorCode.DIRECT_BOOT_UNAVAILABLE,
        )

        assertThat(roundTrip(record)).isEqualTo(record)
    }

    @Test
    fun aTimerRecordWithNoTimersRoundTrips() {
        val record = PendingTimerOccurrence(
            timers = emptyList(),
            triggeredAt = triggered,
            dismissedAt = null,
            outcome = "RINGING",
        )

        assertThat(roundTrip(record)).isEqualTo(record)
    }

    @Test
    fun decodedRecordsCarryTheSourceTheyWereWrittenWith() {
        val alarm = PendingOccurrenceCodec.decode("2|ALARM|7|1767250800000|1767250805000|-1|RINGING")
        val timer = PendingOccurrenceCodec.decode("2|TIMER|1767250805000|-1|STOPPED|DIRECT_BOOT_UNAVAILABLE|3:Tea")

        assertThat(alarm?.source).isEqualTo(OccurrenceSource.ALARM)
        assertThat(timer?.source).isEqualTo(OccurrenceSource.TIMER)
    }

    /** Written before the discriminator existed: five fields, no version tag. It must decode as an alarm, never be dropped. */
    @Test
    fun aLegacyAlarmRecordDecodesAsAnAlarm() {
        val decoded = PendingOccurrenceCodec.decode("42|1767250800000|1767250805000|-1|RINGING")

        assertThat(decoded).isEqualTo(
            PendingOccurrence(AlarmId(42), scheduled, triggered, null, "RINGING"),
        )
        assertThat(decoded?.source).isEqualTo(OccurrenceSource.ALARM)
    }

    @Test
    fun aLegacyAlarmRecordWithADismissTimeDecodes() {
        val decoded = PendingOccurrenceCodec.decode("42|1767250800000|1767250805000|1767250900000|DISMISSED")

        assertThat(decoded).isEqualTo(
            PendingOccurrence(AlarmId(42), scheduled, triggered, dismissed, "DISMISSED"),
        )
    }

    @Test
    fun malformedRecordsDecodeToNullRatherThanThrowing() {
        val malformed = listOf(
            "",
            "garbage",
            "3|ALARM|1|2|3|-1|RINGING",
            "2|BOGUS|1|2|3|-1|X",
            "2|TIMER|notanumber|-1|STOPPED|DIRECT_BOOT_UNAVAILABLE|",
            "2|TIMER|1|-1|STOPPED|NOT_A_CODE|",
            "2|TIMER|1|-1|STOPPED|DIRECT_BOOT_UNAVAILABLE|nocolon",
            "2|ALARM|1|2|3|-1",
            "x|1|2|-1|RINGING",
        )

        malformed.forEach { value ->
            assertThat(PendingOccurrenceCodec.decode(value)).isNull()
        }
    }

    /** Two showings of the same ring with different attribution are one record: identity is the first ring. */
    @Test
    fun timerRecordsWithTheSameFirstRingShareAnIdentity() {
        val first = PendingTimerOccurrence(
            timers = listOf(TimerAttribution(TimerId(3), "Tea")),
            triggeredAt = triggered,
            dismissedAt = null,
            outcome = "RINGING",
        )
        val second = PendingTimerOccurrence(
            timers = listOf(TimerAttribution(TimerId(9), "Eggs")),
            triggeredAt = triggered,
            dismissedAt = dismissed,
            outcome = "STOPPED",
        )

        assertThat(PendingOccurrenceCodec.identity(first)).isEqualTo(PendingOccurrenceCodec.identity(second))
    }

    @Test
    fun anAlarmAndATimerNeverShareAnIdentityEvenAtTheSameMillis() {
        val alarm = PendingOccurrence(AlarmId(7), triggered, triggered, null, "RINGING")
        val timer = PendingTimerOccurrence(
            timers = listOf(TimerAttribution(TimerId(7), "Tea")),
            triggeredAt = triggered,
            dismissedAt = null,
            outcome = "RINGING",
        )

        assertThat(PendingOccurrenceCodec.identity(alarm)).isNotEqualTo(PendingOccurrenceCodec.identity(timer))
    }

    @Test
    fun twoAlarmsDifferingOnlyInScheduledTimeHaveDifferentIdentities() {
        val earlier = PendingOccurrence(AlarmId(7), scheduled, triggered, null, "RINGING")
        val later = earlier.copy(scheduledFor = scheduled.plusSeconds(86_400))

        assertThat(PendingOccurrenceCodec.identity(earlier)).isNotEqualTo(PendingOccurrenceCodec.identity(later))
    }
}
