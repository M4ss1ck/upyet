package dev.upyet.timer.scheduling

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.upyet.core.notifications.AlarmNotifications
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRunState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.Instant

/** ADR 0012: the missed notice is id 1103 on `timer_missed`, silent, and stays until it is cleared. */
@RunWith(RobolectricTestRunner::class)
class AndroidTimerMissedNoticesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notices = AndroidTimerMissedNotices(context)

    private fun missed(id: Long, label: String) =
        Timer.restore(TimerId(id), Duration.ofMinutes(5), label, null, false, TimerRunState.Missed, Instant.EPOCH)

    @Test
    fun oneLabelledTimerIsNamedOnTheTimerMissedChannel() {
        notices.post(listOf(missed(1, "Pasta")))

        val posted = shadowOf(manager).getNotification(AlarmNotifications.NOTIFICATION_ID_TIMER_MISSED)
        assertThat(posted.channelId).isEqualTo("timer_missed")
        assertThat(posted.extras.getString(Notification.EXTRA_TITLE)).isEqualTo("Timer missed")
        assertThat(posted.extras.getString(Notification.EXTRA_TEXT)).isEqualTo("Pasta")
        assertThat(posted.flags and Notification.FLAG_ONGOING_EVENT).isEqualTo(0)
        assertThat(posted.flags and Notification.FLAG_AUTO_CANCEL).isEqualTo(0)
        assertThat(notices.isShowing()).isTrue()
    }

    @Test
    fun severalTimersAreCounted() {
        notices.post(listOf(missed(1, "Pasta"), missed(2, "Tea")))

        val posted = shadowOf(manager).getNotification(AlarmNotifications.NOTIFICATION_ID_TIMER_MISSED)
        assertThat(posted.extras.getString(Notification.EXTRA_TITLE)).isEqualTo("2 timers missed")
        assertThat(posted.extras.getString(Notification.EXTRA_TEXT))
            .isEqualTo("They ended while UpYet could not ring them. Reset them from the Timer tab.")
    }

    @Test
    fun clearRemovesIt() {
        notices.post(listOf(missed(1, "")))

        notices.clear()

        assertThat(notices.isShowing()).isFalse()
        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }
}
