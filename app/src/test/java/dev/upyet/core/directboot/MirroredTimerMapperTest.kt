package dev.upyet.core.directboot

import com.google.common.truth.Truth.assertThat
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRunState
import dev.upyet.timer.domain.TimerTransition
import org.junit.Test
import java.time.Duration
import java.time.Instant

class MirroredTimerMapperTest {
    private val createdAt = Instant.parse("2026-10-26T07:00:00Z")
    private val anchorWall = Instant.parse("2026-10-26T08:00:00Z")
    private val anchor = TimerAnchor(Duration.ofHours(2), anchorWall)

    private fun timer(runState: TimerRunState) = Timer.restore(
        id = TimerId(4),
        length = Duration.ofMinutes(10),
        label = "Pasta",
        ringtoneUri = "content://media/internal/audio/media/9",
        evidenceEnabled = true,
        runState = runState,
        createdAt = createdAt,
    )

    /** A boot later: elapsed realtime restarted near zero while the wall clock moved on. */
    private fun afterReboot(wall: String) = TimerAnchor(Duration.ofSeconds(40), Instant.parse(wall))

    @Test
    fun aRunningTimerMirrorsItsWallClockAnchorAndEndInstant() {
        val mirrored = timer(TimerRunState.Running(Duration.ofMinutes(5), anchor)).toMirrored()

        assertThat(mirrored).isEqualTo(
            MirroredTimer(
                timerId = TimerId(4),
                state = MirroredTimerState.RUNNING,
                length = Duration.ofMinutes(10),
                remainingAtAnchor = Duration.ofMinutes(5),
                wallClockAnchor = anchorWall,
                endsAt = Instant.parse("2026-10-26T08:05:00Z"),
                label = "Pasta",
                ringtoneUri = "content://media/internal/audio/media/9",
                evidenceEnabled = true,
            ),
        )
    }

    @Test
    fun statesWithoutAnAnchorMirrorNoEndInstant() {
        assertThat(timer(TimerRunState.Paused(Duration.ofSeconds(30))).toMirrored().remainingAtAnchor).isEqualTo(Duration.ofSeconds(30))
        listOf(TimerRunState.Idle, TimerRunState.Paused(Duration.ofSeconds(30)), TimerRunState.Ringing, TimerRunState.Missed).forEach {
            val mirrored = timer(it).toMirrored()
            assertThat(mirrored.wallClockAnchor).isNull()
            assertThat(mirrored.endsAt).isNull()
        }
    }

    @Test
    fun statesWithoutAnAnchorRoundTripExactly() {
        listOf(TimerRunState.Idle, TimerRunState.Paused(Duration.ofSeconds(30)), TimerRunState.Ringing, TimerRunState.Missed).forEach {
            val original = timer(it)
            assertThat(original.toMirrored().toTimer(afterReboot("2026-10-26T09:00:00Z"), createdAt)).isEqualTo(original)
        }
    }

    @Test
    fun aRestoredRunningTimerCountsDownOnTheWallClockAfterAReboot() {
        val now = afterReboot("2026-10-26T08:03:00Z")

        val restored = timer(TimerRunState.Running(Duration.ofMinutes(5), anchor)).toMirrored().toTimer(now, createdAt)

        assertThat(restored?.remaining(now)).isEqualTo(Duration.ofMinutes(2))
        // The re-based elapsed anchor keeps counting on this boot's elapsed clock.
        val later = TimerAnchor(now.elapsedRealtime.plusSeconds(60), now.wallClock.plusSeconds(60))
        assertThat(restored?.remaining(later)).isEqualTo(Duration.ofMinutes(1))
    }

    /** Discovery needs to know how overdue a timer is, so restoring must not clamp it to zero. */
    @Test
    fun aRestoredRunningTimerKeepsHowOverdueItIs() {
        val running = timer(TimerRunState.Running(Duration.ofMinutes(5), anchor)).toMirrored()

        val twoMinutesLate = afterReboot("2026-10-26T08:07:00Z")
        val thirtySecondsLate = afterReboot("2026-10-26T08:05:30Z")

        val missed = checkNotNull(running.toTimer(twoMinutesLate, createdAt)).onDiscovery(twoMinutesLate, afterBoot = true)
        val late = checkNotNull(running.toTimer(thirtySecondsLate, createdAt)).onDiscovery(thirtySecondsLate, afterBoot = true)

        assertThat((missed as TimerTransition.Applied).timer.runState).isEqualTo(TimerRunState.Missed)
        assertThat((late as TimerTransition.Applied).timer.runState).isEqualTo(TimerRunState.Running(Duration.ZERO, thirtySecondsLate))
    }

    /** AGENTS.md §6a: a wall clock set backwards is a negative delta and counts as no time passed, never as time added. */
    @Test
    fun aWallClockSetBeforeTheAnchorAddsNoTime() {
        val now = afterReboot("2026-10-26T07:30:00Z")

        val restored = timer(TimerRunState.Running(Duration.ofMinutes(5), anchor)).toMirrored().toTimer(now, createdAt)

        assertThat(restored?.remaining(now)).isEqualTo(Duration.ofMinutes(5))
    }

    @Test
    fun aRunningRecordWithoutItsAnchorRestoresToNothing() {
        val broken = timer(TimerRunState.Running(Duration.ofMinutes(5), anchor)).toMirrored().copy(wallClockAnchor = null)

        assertThat(broken.toTimer(afterReboot("2026-10-26T08:03:00Z"), createdAt)).isNull()
    }

    @Test
    fun aPausedRecordWithoutItsRemainingRestoresToNothing() {
        val broken = timer(TimerRunState.Paused(Duration.ofSeconds(30))).toMirrored().copy(remainingAtAnchor = null)

        assertThat(broken.toTimer(afterReboot("2026-10-26T08:03:00Z"), createdAt)).isNull()
    }
}
