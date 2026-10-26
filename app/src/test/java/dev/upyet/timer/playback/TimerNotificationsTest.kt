package dev.upyet.timer.playback

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.upyet.timer.domain.TimerId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.Instant

/** ADR 0012's `timer_ringing` notification and issue #14's heads-up variant B, as the platform receives them. */
@RunWith(RobolectricTestRunner::class)
class TimerNotificationsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifications = TimerNotifications(context)

    private fun ringing(id: Long, label: String = "", evidence: Boolean = false) =
        RingingTimer(TimerId(id), label, Duration.ofMinutes(5), null, evidence, Instant.EPOCH)

    private val Notification.title get() = extras.getString(Notification.EXTRA_TITLE)
    private val Notification.text get() = extras.getString(Notification.EXTRA_TEXT)
    private val Notification.actionTitles get() = actions.orEmpty().map { it.title.toString() }

    /** The command an action sends to the service: its action string and, for one timer, the timer id. */
    private fun Notification.Action.command(): Pair<String?, Long> {
        val intent = shadowOf(actionIntent).savedIntent
        return intent.action to intent.getLongExtra("timer_id", -1L)
    }

    @Test
    fun itIsAnOngoingAlarmOnTheTimerRingingChannelWithAFullScreenIntentAndNoGroup() {
        val posted = notifications.build(listOf(ringing(1)))

        assertThat(posted.channelId).isEqualTo("timer_ringing")
        assertThat(posted.category).isEqualTo(Notification.CATEGORY_ALARM)
        assertThat(posted.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(posted.fullScreenIntent).isNotNull()
        assertThat(posted.contentIntent).isNotNull()
        assertThat(posted.group).isNull()
    }

    @Test
    fun oneUnlabelledTimerIsTitledTimerAndOffersStopAndAMinuteForThatTimer() {
        val posted = notifications.build(listOf(ringing(7)))

        assertThat(posted.title).isEqualTo("Timer")
        assertThat(posted.text).isEqualTo("1 timer ringing")
        assertThat(posted.actionTitles).containsExactly("Stop", "+1 min").inOrder()
        assertThat(posted.actions.map { it.command() }).containsExactly(
            TimerPlaybackService.ACTION_STOP to 7L,
            TimerPlaybackService.ACTION_ADD_MINUTE to 7L,
        ).inOrder()
    }

    @Test
    fun oneLabelledTimerCarriesItsLabelInTheTitle() {
        assertThat(notifications.build(listOf(ringing(1, label = "Pasta"))).title).isEqualTo("Timer · Pasta")
    }

    @Test
    fun severalTimersAreCountedAndOfferStopAll() {
        val posted = notifications.build(listOf(ringing(1, "Pasta"), ringing(2), ringing(3)))

        assertThat(posted.title).isEqualTo("3 timers ringing")
        assertThat(posted.text).isEqualTo("3 timers ringing")
        assertThat(posted.actionTitles).containsExactly("Stop all")
        assertThat(posted.actions.single().command().first).isEqualTo(TimerPlaybackService.ACTION_STOP_ALL)
    }

    @Test
    fun aRecordingTimerLeavesOnlyTapToOpenAndNoActionThatEndsARing() {
        val posted = notifications.build(listOf(ringing(1), ringing(2, evidence = true)))

        assertThat(posted.title).isEqualTo("2 timers ringing")
        assertThat(posted.text).isEqualTo("Tap to open")
        assertThat(posted.actions).isNull()
    }

    @Test
    fun beforeTheFirstTimerIsReadItClaimsNoCount() {
        val posted = notifications.build(emptyList())

        assertThat(posted.title).isEqualTo("Timer")
        assertThat(posted.text).isEqualTo("Tap to open")
        assertThat(posted.actions).isNull()
    }
}
