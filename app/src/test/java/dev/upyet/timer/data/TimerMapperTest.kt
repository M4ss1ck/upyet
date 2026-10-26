package dev.upyet.timer.data

import com.google.common.truth.Truth.assertThat
import dev.upyet.core.database.TimerEntity
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRunState
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TimerMapperTest {
    private val createdAt = Instant.parse("2026-10-26T07:00:00Z")
    private val anchor = TimerAnchor(Duration.ofMillis(123_456), Instant.parse("2026-10-26T08:00:00Z"))

    private fun timer(runState: TimerRunState) = Timer.restore(
        id = TimerId(4),
        length = Duration.ofMinutes(10),
        label = "Pasta",
        ringtoneUri = "content://media/internal/audio/media/9",
        evidenceEnabled = true,
        runState = runState,
        createdAt = createdAt,
    )

    @Test
    fun everyRunStateRoundTrips() {
        listOf(
            TimerRunState.Idle,
            TimerRunState.Running(Duration.ofSeconds(425), anchor),
            TimerRunState.Paused(Duration.ofSeconds(30)),
            TimerRunState.Ringing,
            TimerRunState.Missed,
        ).forEach { state ->
            val original = timer(state)
            assertThat(original.toEntity().toDomain()).isEqualTo(original)
        }
    }

    @Test
    fun aRunningTimerStoresBothAnchorsAndItsRemaining() {
        val entity = timer(TimerRunState.Running(Duration.ofSeconds(425), anchor)).toEntity()

        assertThat(entity).isEqualTo(
            TimerEntity(
                id = 4,
                lengthMillis = 600_000,
                label = "Pasta",
                ringtoneUri = "content://media/internal/audio/media/9",
                evidenceEnabled = true,
                runState = "RUNNING",
                remainingAtAnchorMillis = 425_000,
                anchorElapsedRealtimeMillis = 123_456,
                anchorWallClockMillis = anchor.wallClock.toEpochMilli(),
                createdAt = createdAt.toEpochMilli(),
            ),
        )
    }

    @Test
    fun aStateWithoutAnAnchorStoresNoAnchor() {
        val entity = timer(TimerRunState.Paused(Duration.ofSeconds(30))).toEntity()

        assertThat(entity.remainingAtAnchorMillis).isEqualTo(30_000)
        assertThat(entity.anchorElapsedRealtimeMillis).isNull()
        assertThat(entity.anchorWallClockMillis).isNull()
    }

    @Test(expected = IllegalStateException::class)
    fun anUnknownRunStateIsAnError() {
        timer(TimerRunState.Idle).toEntity().copy(runState = "BOILING").toDomain()
    }

    @Test(expected = IllegalStateException::class)
    fun aRunningRowWithoutItsAnchorIsAnError() {
        timer(TimerRunState.Running(Duration.ofSeconds(1), anchor)).toEntity().copy(anchorWallClockMillis = null).toDomain()
    }
}
