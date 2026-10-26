package dev.upyet.timer.scheduling

import com.google.common.truth.Truth.assertThat
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRunState
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** ADR 0008 and 0012: the low-importance notice stays until Reset or delete, and never comes back by itself. */
class MissedNoticeTest {
    private fun timer(id: Long, runState: TimerRunState) =
        Timer.restore(TimerId(id), Duration.ofMinutes(5), "Tea", null, false, runState, Instant.EPOCH)

    private val missed = timer(1, TimerRunState.Missed)
    private val idle = timer(2, TimerRunState.Idle)

    @Test
    fun aNewlyMissedTimerPostsTheNotice() {
        assertThat(MissedNotice.decide(listOf(missed, idle), newlyMissed = true, showing = false))
            .isEqualTo(MissedNotice.Post(listOf(missed)))
    }

    @Test
    fun aNoticeTheUserSwipedAwayIsNotPostedAgainByAnUnrelatedChange() {
        assertThat(MissedNotice.decide(listOf(missed), newlyMissed = false, showing = false)).isEqualTo(MissedNotice.Leave)
    }

    @Test
    fun aShowingNoticeFollowsTheMissedTimersThatRemain() {
        val second = timer(3, TimerRunState.Missed)

        assertThat(MissedNotice.decide(listOf(missed, second), newlyMissed = false, showing = true))
            .isEqualTo(MissedNotice.Post(listOf(missed, second)))
    }

    @Test
    fun resettingOrDeletingTheLastMissedTimerClearsTheNotice() {
        assertThat(MissedNotice.decide(listOf(idle), newlyMissed = false, showing = true)).isEqualTo(MissedNotice.Clear)
    }

    @Test
    fun nothingMissedAndNothingShowingDoesNothing() {
        assertThat(MissedNotice.decide(listOf(idle), newlyMissed = false, showing = false)).isEqualTo(MissedNotice.Leave)
    }
}
