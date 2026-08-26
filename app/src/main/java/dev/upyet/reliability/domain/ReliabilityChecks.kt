package dev.upyet.reliability.domain

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.R
import dev.upyet.alarm.playback.AlarmVolume
import dev.upyet.alarm.scheduling.AlarmScheduler
import dev.upyet.alarm.scheduling.ExactAlarmAccess
import dev.upyet.core.notifications.AlarmNotifications
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReliabilityChecks @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exact: ExactAlarmAccess,
    private val notifications: AlarmNotifications,
    private val scheduler: AlarmScheduler,
    private val alarmVolume: AlarmVolume,
) {
    fun evaluate(): List<ReliabilityCheck> {
        val nextTrigger = scheduler.nextScheduledTrigger()
        // Read once: two reads can straddle a volume-key press and report BLOCKED with the audible copy.
        val silentAlarmStream = alarmVolume.isSilent()
        val barelyAudibleAlarmStream = alarmVolume.isBarelyAudible()
        return listOf(
            ReliabilityCheck(
                "exact",
                R.string.reliability_exact,
                if (exact.canScheduleExact()) ReliabilityStatus.OK else ReliabilityStatus.BLOCKED,
                R.string.reliability_exact_explanation,
                settingsIntent = exact.settingsIntent(),
            ),
            ReliabilityCheck(
                "notifications",
                R.string.reliability_notifications,
                if (notifications.areNotificationsEnabled()) ReliabilityStatus.OK else ReliabilityStatus.BLOCKED,
                R.string.reliability_notifications_explanation,
                settingsIntent = Intent(
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            ),
            ReliabilityCheck(
                "alarm_volume",
                R.string.reliability_volume,
                when {
                    silentAlarmStream -> ReliabilityStatus.BLOCKED
                    barelyAudibleAlarmStream -> ReliabilityStatus.WARNING
                    else -> ReliabilityStatus.OK
                },
                when {
                    silentAlarmStream -> R.string.reliability_volume_silent
                    barelyAudibleAlarmStream -> R.string.reliability_volume_low
                    else -> R.string.reliability_volume_explanation
                },
                settingsIntent = alarmVolume.settingsIntent(),
            ),
            ReliabilityCheck(
                "fullscreen",
                R.string.reliability_fullscreen,
                if (notifications.canUseFullScreenIntent()) ReliabilityStatus.OK else ReliabilityStatus.WARNING,
                R.string.reliability_fullscreen_explanation,
                settingsIntent = fullScreenIntentSettings(),
            ),
            ReliabilityCheck(
                "camera",
                R.string.reliability_camera,
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    ReliabilityStatus.OK
                } else {
                    ReliabilityStatus.WARNING
                },
                R.string.reliability_camera_explanation,
                settingsIntent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                ).setData("package:${context.packageName}".toUri()),
            ),
            batteryCheck(),
            ReliabilityCheck(
                "direct_boot",
                R.string.reliability_direct_boot,
                ReliabilityStatus.OK,
                R.string.reliability_direct_boot_explanation,
            ),
            ReliabilityCheck(
                "next_alarm",
                R.string.reliability_next_alarm,
                if (nextTrigger == null) ReliabilityStatus.WARNING else ReliabilityStatus.OK,
                R.string.reliability_next_alarm_explanation,
                nextTrigger,
            ),
        )
    }

    /**
     * The dedicated full-screen-intent settings screen only exists from API 34; below it the permission is
     * granted by the platform, so the app's notification settings are the closest useful destination.
     */
    private fun fullScreenIntentSettings(): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData("package:${context.packageName}".toUri())
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

    private fun batteryCheck(): ReliabilityCheck {
        val manager = context.getSystemService(PowerManager::class.java)
        val ignoring = manager?.isIgnoringBatteryOptimizations(context.packageName) == true
        return ReliabilityCheck(
            "battery",
            R.string.reliability_battery,
            if (ignoring) ReliabilityStatus.OK else ReliabilityStatus.WARNING,
            if (ignoring) R.string.reliability_battery_active else R.string.reliability_battery_informational,
        )
    }
}
