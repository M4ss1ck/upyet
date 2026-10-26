package dev.upyet.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * ADR 0012's channel table. The application creates every channel at startup, so these are what the system
 * notification settings show on a fresh install, before anything has rung or run.
 */
@RunWith(RobolectricTestRunner::class)
class AlarmNotificationsChannelsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun theApplicationCreatesAllFiveChannelsAtStartupAndPostsNothing() {
        // No ensureChannels() call here: Robolectric has already run UpYetApplication.onCreate.
        assertThat(manager.notificationChannels.map { it.id }).containsExactly(
            AlarmNotifications.CHANNEL_RINGING,
            AlarmNotifications.CHANNEL_UPCOMING,
            AlarmNotifications.CHANNEL_TIMER_RINGING,
            AlarmNotifications.CHANNEL_CLOCK_TOOL_ONGOING,
            AlarmNotifications.CHANNEL_TIMER_MISSED,
        )
        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }

    @Test
    fun channelIdsMatchAdr0012sTable() {
        assertThat(AlarmNotifications.CHANNEL_RINGING).isEqualTo("alarm_ringing")
        assertThat(AlarmNotifications.CHANNEL_UPCOMING).isEqualTo("alarm_upcoming")
        assertThat(AlarmNotifications.CHANNEL_TIMER_RINGING).isEqualTo("timer_ringing")
        assertThat(AlarmNotifications.CHANNEL_CLOCK_TOOL_ONGOING).isEqualTo("clock_tool_ongoing")
        assertThat(AlarmNotifications.CHANNEL_TIMER_MISSED).isEqualTo("timer_missed")
    }

    @Test
    fun alarmChannelsKeepTheirSettings() {
        AlarmNotifications(context).ensureChannels()

        assertSilentPublic(channel(AlarmNotifications.CHANNEL_RINGING), NotificationManager.IMPORTANCE_HIGH)
        assertSilentPublic(channel(AlarmNotifications.CHANNEL_UPCOMING), NotificationManager.IMPORTANCE_LOW)
        // Never given one, and adding one now would change what upgrading users see in settings.
        assertThat(channel(AlarmNotifications.CHANNEL_RINGING).description).isNull()
        assertThat(channel(AlarmNotifications.CHANNEL_UPCOMING).description).isNull()
    }

    /** Sound comes from TimerPlaybackService, exactly as the alarm's comes from AlarmPlaybackService. */
    @Test
    fun timerRingingIsHighSilentAndPublic() {
        AlarmNotifications(context).ensureChannels()

        val timerRinging = channel(AlarmNotifications.CHANNEL_TIMER_RINGING)
        assertSilentPublic(timerRinging, NotificationManager.IMPORTANCE_HIGH)
        assertThat(timerRinging.name.toString()).isEqualTo("Timer ringing")
        assertThat(timerRinging.description).isNotEmpty()
    }

    @Test
    fun theOngoingAndMissedChannelsAreLow() {
        AlarmNotifications(context).ensureChannels()

        val ongoing = channel(AlarmNotifications.CHANNEL_CLOCK_TOOL_ONGOING)
        val missed = channel(AlarmNotifications.CHANNEL_TIMER_MISSED)
        assertSilentPublic(ongoing, NotificationManager.IMPORTANCE_LOW)
        assertSilentPublic(missed, NotificationManager.IMPORTANCE_LOW)
        assertThat(ongoing.description).isNotEmpty()
        assertThat(missed.description).isNotEmpty()
    }

    /** A user who demoted timers keeps that choice: re-creating an existing channel never resets importance. */
    @Test
    fun creatingTheChannelsAgainIsANoOp() {
        val notifications = AlarmNotifications(context)
        notifications.ensureChannels()
        val demoted = channel(AlarmNotifications.CHANNEL_TIMER_RINGING).apply {
            importance = NotificationManager.IMPORTANCE_LOW
        }
        manager.createNotificationChannel(demoted)

        notifications.ensureChannels()

        assertThat(manager.notificationChannels).hasSize(5)
        assertThat(channel(AlarmNotifications.CHANNEL_TIMER_RINGING).importance)
            .isEqualTo(NotificationManager.IMPORTANCE_LOW)
        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }

    /** A shared id would let a timer cancel or replace an alarm notification (ADR 0012). */
    @Test
    fun alarmAndClockToolIdsNeverCollide() {
        val alarmIds = listOf(
            AlarmNotifications.NOTIFICATION_ID_RINGING,
            AlarmNotifications.REQUEST_SNOOZE,
            AlarmNotifications.REQUEST_DISMISS,
            AlarmNotifications.NOTIFICATION_ID_UPCOMING,
        )
        val clockToolIds = listOf(
            AlarmNotifications.NOTIFICATION_ID_TIMER_RINGING,
            AlarmNotifications.NOTIFICATION_ID_TIMER_RUNNING,
            AlarmNotifications.NOTIFICATION_ID_TIMER_MISSED,
            AlarmNotifications.NOTIFICATION_ID_STOPWATCH,
        )

        assertThat(alarmIds).containsExactly(1001, 1002, 1003, 1004).inOrder()
        assertThat(clockToolIds).containsExactly(1101, 1102, 1103, 1104).inOrder()
        assertThat((alarmIds + clockToolIds).toSet()).hasSize(alarmIds.size + clockToolIds.size)
    }

    private fun channel(id: String): NotificationChannel = requireNotNull(manager.getNotificationChannel(id)) { id }

    private fun assertSilentPublic(channel: NotificationChannel, importance: Int) {
        assertThat(channel.importance).isEqualTo(importance)
        assertThat(channel.sound).isNull()
        assertThat(channel.shouldVibrate()).isFalse()
        assertThat(channel.lockscreenVisibility).isEqualTo(Notification.VISIBILITY_PUBLIC)
    }
}
