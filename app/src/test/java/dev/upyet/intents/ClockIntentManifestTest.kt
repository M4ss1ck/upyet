package dev.upyet.intents

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** ADR 0007 at the manifest: six actions reach the trampoline, and SNOOZE_ALARM reaches nothing of ours. */
@RunWith(RobolectricTestRunner::class)
class ClockIntentManifestTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val trampoline = ClockIntentActivity::class.java.name

    private fun resolved(action: String, data: Uri? = null): List<String> {
        val intent = Intent(action).addCategory(Intent.CATEGORY_DEFAULT).apply { data?.let { setData(it) } }
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.packageName == context.packageName }
            .map { it.activityInfo.name }
    }

    @Test fun eachSupportedActionResolvesToTheTrampoline() {
        listOf(
            ClockIntentActions.SET_ALARM,
            ClockIntentActions.SHOW_ALARMS,
            ClockIntentActions.SET_TIMER,
            ClockIntentActions.SHOW_TIMERS,
            ClockIntentActions.DISMISS_TIMER,
            ClockIntentActions.DISMISS_ALARM,
        ).forEach { assertThat(resolved(it)).containsExactly(trampoline) }
    }

    @Test fun dismissTimerWithAContentUriResolvesToo() {
        assertThat(resolved(ClockIntentActions.DISMISS_TIMER, Uri.parse("content://dev.example/timers/3"))).containsExactly(trampoline)
    }

    @Test fun snoozeAlarmResolvesToNothingOfOurs() {
        assertThat(resolved("android.intent.action.SNOOZE_ALARM")).isEmpty()
    }

    @Test fun theTrampolineIsGuardedByTheSetAlarmPermissionAndNotDirectBootAware() {
        val info = context.packageManager.getActivityInfo(android.content.ComponentName(context, trampoline), 0)
        assertThat(info.permission).isEqualTo("com.android.alarm.permission.SET_ALARM")
        assertThat(info.directBootAware).isFalse()
        assertThat(info.exported).isTrue()
    }

    // Two requests in quick succession must each get an instance; the second would otherwise be lost (see manifest).
    @Test fun everyRequestGetsItsOwnTrampolineInstance() {
        val info = context.packageManager.getActivityInfo(android.content.ComponentName(context, trampoline), 0)
        assertThat(info.documentLaunchMode).isEqualTo(android.content.pm.ActivityInfo.DOCUMENT_LAUNCH_ALWAYS)
    }
}
