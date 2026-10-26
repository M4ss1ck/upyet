package dev.upyet.core.directboot

import com.google.common.truth.Truth.assertThat
import dev.upyet.timer.domain.TimerId
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * The timer mirror is a positional `|` string in device-protected storage, so its encoding is a compatibility
 * contract with every earlier build. These pin it on the JVM; `DirectBootTimerStoreTest` pins it against real
 * device-protected storage.
 */
class MirroredTimerCodecTest {
    private val running = MirroredTimer(
        timerId = TimerId(7),
        state = MirroredTimerState.RUNNING,
        length = Duration.ofMinutes(10),
        remainingAtAnchor = Duration.ofSeconds(425),
        wallClockAnchor = Instant.parse("2026-10-26T08:00:00Z"),
        endsAt = Instant.parse("2026-10-26T08:07:05Z"),
        label = "Pasta | eggs: 100% ñ",
        ringtoneUri = "content://media/internal/audio/media/42",
        evidenceEnabled = true,
        writtenWhileLocked = true,
    )

    private fun roundTrip(record: MirroredTimer): MirroredTimer? =
        MirroredTimerCodec.decode(MirroredTimerCodec.key(record.timerId), MirroredTimerCodec.encode(record))

    @Test
    fun aRunningTimerRoundTripsEveryField() {
        assertThat(roundTrip(running)).isEqualTo(running)
    }

    @Test
    fun aLabelWithTheSeparatorDoesNotShiftTheFieldsAfterIt() {
        val decoded = roundTrip(running.copy(label = "a|b|c|d|e|f|g|h|i"))

        assertThat(decoded?.label).isEqualTo("a|b|c|d|e|f|g|h|i")
        assertThat(decoded?.evidenceEnabled).isTrue()
    }

    @Test
    fun everyStateRoundTripsWithItsAbsentFields() {
        val idle = running.copy(
            state = MirroredTimerState.IDLE,
            remainingAtAnchor = null,
            wallClockAnchor = null,
            endsAt = null,
            label = "",
            ringtoneUri = null,
            evidenceEnabled = false,
            writtenWhileLocked = false,
        )
        val paused = idle.copy(state = MirroredTimerState.PAUSED, remainingAtAnchor = Duration.ofSeconds(30))

        listOf(idle, paused, idle.copy(state = MirroredTimerState.RINGING), idle.copy(state = MirroredTimerState.MISSED))
            .forEach { assertThat(roundTrip(it)).isEqualTo(it) }
    }

    /**
     * `writtenWhileLocked` is the field appended last. A record written without it must still parse, and must
     * read as not written while locked, so it can never overwrite Room on unlock.
     */
    @Test
    fun aRecordWithoutTheLastAppendedFieldStillParses() {
        val withoutFlag = MirroredTimerCodec.encode(running).substringBeforeLast('|')

        val decoded = MirroredTimerCodec.decode("timer:7", withoutFlag)

        assertThat(decoded).isEqualTo(running.copy(writtenWhileLocked = false))
    }

    @Test
    fun fieldsAppendedByALaterBuildAreIgnored() {
        val decoded = MirroredTimerCodec.decode("timer:7", MirroredTimerCodec.encode(running) + "|future|42")

        assertThat(decoded).isEqualTo(running)
    }

    @Test
    fun aKnownEncodingDecodes() {
        // Literal written out by hand, so a change to the field order fails here even if encode and decode change together.
        val value = "PAUSED|600000|30000|||Tea||false"

        val decoded = MirroredTimerCodec.decode("timer:3", value)

        assertThat(decoded).isEqualTo(
            MirroredTimer(
                timerId = TimerId(3),
                state = MirroredTimerState.PAUSED,
                length = Duration.ofMinutes(10),
                remainingAtAnchor = Duration.ofSeconds(30),
                wallClockAnchor = null,
                endsAt = null,
                label = "Tea",
                ringtoneUri = null,
                evidenceEnabled = false,
                writtenWhileLocked = false,
            ),
        )
    }

    @Test
    fun malformedRecordsAreDroppedRatherThanThrown() {
        assertThat(MirroredTimerCodec.decode("timer:7", "RUNNING|600000")).isNull()
        assertThat(MirroredTimerCodec.decode("timer:7", "BOILING|600000|1|2|3|x||true")).isNull()
        assertThat(MirroredTimerCodec.decode("timer:x", "IDLE|600000||||x||true")).isNull()
        assertThat(MirroredTimerCodec.decode("alarm:7:MAIN", "IDLE|600000||||x||true")).isNull()
        assertThat(MirroredTimerCodec.decode("timer:7", "IDLE|ten||||x||true")).isNull()
    }
}
