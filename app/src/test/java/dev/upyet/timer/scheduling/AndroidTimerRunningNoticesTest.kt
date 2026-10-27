package dev.upyet.timer.scheduling

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.upyet.core.notifications.AlarmNotifications
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRunState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.Instant

/** ADR 0012: the running notice is id 1102 on `clock_tool_ongoing`, a silent ongoing chronometer with three actions. */
@RunWith(RobolectricTestRunner::class)
class AndroidTimerRunningNoticesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notices = AndroidTimerRunningNotices(context)
    private val endsAt = Instant.parse("2026-10-26T08:05:00Z")

    private fun show(label: String = "Tea", alsoRunning: Int = 0, id: Long = 4): RunningNotice.Show {
        val anchor = TimerAnchor(Duration.ZERO, endsAt.minus(Duration.ofMinutes(5)))
        val timer = Timer.restore(
            TimerId(id),
            Duration.ofMinutes(5),
            label,
            null,
            false,
            TimerRunState.Running(Duration.ofMinutes(5), anchor),
            Instant.EPOCH,
        )
        return RunningNotice.Show(timer, endsAt, alsoRunning)
    }

    private fun posted() = shadowOf(manager).getNotification(AlarmNotifications.NOTIFICATION_ID_TIMER_RUNNING)

    @Test
    fun postsASilentOngoingChronometerCountingDownToItsEnd() {
        notices.show(show())

        val posted = posted()
        assertThat(posted.channelId).isEqualTo("clock_tool_ongoing")
        assertThat(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)).isTrue()
        assertThat(posted.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)).isTrue()
        assertThat(posted.`when`).isEqualTo(endsAt.toEpochMilli())
        assertThat(posted.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        // ADR 0012: never grouped. Not even setSilent, whose compat implementation writes a "silent" group key;
        // the low-importance channel is what keeps it quiet.
        assertThat(posted.group).isNull()
        assertThat(posted.category).isEqualTo(Notification.CATEGORY_STOPWATCH)
        assertThat(posted.extras.getString(Notification.EXTRA_TITLE)).isEqualTo("Tea")
        assertThat(posted.extras.getCharSequence(Notification.EXTRA_TEXT)).isNull()
    }

    @Test
    fun anUnlabelledTimerIsTitledTimer() {
        notices.show(show(label = "  "))

        assertThat(posted().extras.getString(Notification.EXTRA_TITLE)).isEqualTo("Timer")
    }

    @Test
    fun otherRunningTimersAreCounted() {
        notices.show(show(alsoRunning = 2))

        assertThat(posted().extras.getCharSequence(Notification.EXTRA_TEXT).toString()).isEqualTo("2 more timers running")
    }

    @Test
    fun offersPausePlusOneMinuteAndResetAimedAtTheTimer() {
        notices.show(show(id = 4))

        val actions = posted().actions
        assertThat(actions.map { it.title.toString() }).containsExactly("Pause", "+1 min", "Reset").inOrder()
        val intents = actions.map { shadowOf(it.actionIntent).savedIntent }
        assertThat(intents.map(Intent::getAction)).containsExactly(
            "dev.upyet.action.TIMER_PAUSE",
            "dev.upyet.action.TIMER_ADD_MINUTE",
            "dev.upyet.action.TIMER_RESET",
        ).inOrder()
        intents.forEach { intent ->
            assertThat(intent.component?.className).isEqualTo(TimerReceiver::class.java.name)
            assertThat(TimerRunningActions.timerId(intent)).isEqualTo(TimerId(4))
        }
    }

    @Test
    fun cancelRemovesIt() {
        notices.show(show())

        notices.cancel()

        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }
}
