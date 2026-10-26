package dev.upyet.timer.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.scheduling.ExactAlarmAccess
import dev.upyet.timer.domain.TimerId
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration
import java.time.Instant

/** ADR 0009's closed carve-out, against the platform's AlarmManager: one elapsed-realtime alarm, never more. */
@RunWith(RobolectricTestRunner::class)
class AndroidTimerSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val scheduler = AndroidTimerScheduler(context, alarmManager, ExactAlarmAccess(context, alarmManager))

    private val first = TimerAlarm(TimerId(1), Instant.parse("2026-10-26T08:05:00Z"), Duration.ofMinutes(15))
    private val second = TimerAlarm(TimerId(2), Instant.parse("2026-10-26T08:03:00Z"), Duration.ofMinutes(13))

    /** Robolectric starts without exact access, a static that outlives one test, so each test states its own. */
    @Before
    fun grantExactAccess() = ShadowAlarmManager.setCanScheduleExactAlarms(true)

    @After
    fun resetExactAccess() = ShadowAlarmManager.setCanScheduleExactAlarms(false)

    @Test
    fun armingIsOneExactElapsedRealtimeAlarmThatIsNeverTheNextAlarmClock() {
        assertThat(scheduler.arm(first)).isEqualTo(TimerArmResult.Armed)

        val scheduled = shadowOf(alarmManager).scheduledAlarms.single()
        assertThat(scheduled.getType()).isEqualTo(AlarmManager.ELAPSED_REALTIME_WAKEUP)
        assertThat(scheduled.triggerAtMs).isEqualTo(Duration.ofMinutes(15).toMillis())
        assertThat(scheduled.isAllowWhileIdle).isTrue()
        assertThat(alarmManager.nextAlarmClock).isNull()
    }

    @Test
    fun armingAgainReplacesTheAlarmAndCarriesTheNewTimer() {
        scheduler.arm(first)
        scheduler.arm(second)

        val scheduled = shadowOf(alarmManager).scheduledAlarms.single()
        assertThat(scheduled.triggerAtMs).isEqualTo(Duration.ofMinutes(13).toMillis())
        val operation = PendingIntent.getBroadcast(
            context,
            TimerPendingIntents.REQUEST_CODE,
            TimerPendingIntents.expiryIntent(context, null),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        val intent = shadowOf(operation).savedIntent
        assertThat(TimerPendingIntents.delivered(intent)).isEqualTo(TimerId(2) to Instant.parse("2026-10-26T08:03:00Z"))
    }

    @Test
    fun cancelRemovesTheAlarm() {
        scheduler.arm(first)

        scheduler.cancel()
        scheduler.cancel()

        assertThat(shadowOf(alarmManager).scheduledAlarms).isEmpty()
    }

    @Test
    fun withoutExactAccessNothingIsArmedAtAllNotEvenInexactly() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        assertThat(scheduler.arm(first)).isEqualTo(TimerArmResult.ExactAlarmsUnavailable)
        assertThat(shadowOf(alarmManager).scheduledAlarms).isEmpty()
    }
}
