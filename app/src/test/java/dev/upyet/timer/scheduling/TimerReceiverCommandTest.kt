package dev.upyet.timer.scheduling

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Literal action strings, as the platform delivers them, so a renamed constant cannot hide a lost delivery. */
class TimerReceiverCommandTest {
    @Test
    fun theTimerAlarmIsAnExpiry() {
        assertThat(TimerReceiverCommand.of("dev.upyet.action.TIMER_EXPIRED")).isEqualTo(TimerReceiverCommand.EXPIRY)
    }

    @Test
    fun bothBootBroadcastsAreDiscoveryAfterBoot() {
        assertThat(TimerReceiverCommand.of("android.intent.action.LOCKED_BOOT_COMPLETED")).isEqualTo(TimerReceiverCommand.BOOT)
        assertThat(TimerReceiverCommand.of("android.intent.action.BOOT_COMPLETED")).isEqualTo(TimerReceiverCommand.BOOT)
    }

    @Test
    fun anUpdateAndRestoredExactAccessReArm() {
        assertThat(TimerReceiverCommand.of("android.intent.action.MY_PACKAGE_REPLACED")).isEqualTo(TimerReceiverCommand.REARM)
        assertThat(TimerReceiverCommand.of("android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"))
            .isEqualTo(TimerReceiverCommand.REARM)
    }

    @Test
    fun aWallClockChangeRecomputesRemainingTime() {
        assertThat(TimerReceiverCommand.of("android.intent.action.TIME_SET")).isEqualTo(TimerReceiverCommand.WALL_CLOCK_CHANGED)
    }

    @Test
    fun theRunningNotificationActionsMapToTheirCommands() {
        assertThat(TimerReceiverCommand.of("dev.upyet.action.TIMER_PAUSE")).isEqualTo(TimerReceiverCommand.PAUSE)
        assertThat(TimerReceiverCommand.of("dev.upyet.action.TIMER_ADD_MINUTE")).isEqualTo(TimerReceiverCommand.ADD_MINUTE)
        assertThat(TimerReceiverCommand.of("dev.upyet.action.TIMER_RESET")).isEqualTo(TimerReceiverCommand.RESET)
    }

    @Test
    fun aTimeZoneChangeReArmsNothing() {
        assertThat(TimerReceiverCommand.of("android.intent.action.TIMEZONE_CHANGED")).isNull()
    }

    @Test
    fun unlockIsNotHandledHereBecauseItFollowsReconciliation() {
        assertThat(TimerReceiverCommand.of("android.intent.action.USER_UNLOCKED")).isNull()
        assertThat(TimerReceiverCommand.of(null)).isNull()
    }
}
