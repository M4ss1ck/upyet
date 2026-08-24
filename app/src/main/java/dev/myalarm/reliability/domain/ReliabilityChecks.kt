package dev.myalarm.reliability.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.myalarm.R
import dev.myalarm.alarm.scheduling.AlarmScheduler
import dev.myalarm.alarm.scheduling.ExactAlarmAccess
import dev.myalarm.core.notifications.AlarmNotifications
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReliabilityChecks @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exact: ExactAlarmAccess,
    private val notifications: AlarmNotifications,
    private val scheduler: AlarmScheduler,
) {
    fun evaluate(): List<ReliabilityCheck> = listOf(
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
            settingsIntent = android.content.Intent(
                android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            ).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
        ),
        ReliabilityCheck(
            "fullscreen",
            R.string.reliability_fullscreen,
            if (notifications.canUseFullScreenIntent()) ReliabilityStatus.OK else ReliabilityStatus.WARNING,
            R.string.reliability_fullscreen_explanation,
            settingsIntent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            ).setData(android.net.Uri.parse("package:${context.packageName}")),
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
            settingsIntent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            ).setData(android.net.Uri.parse("package:${context.packageName}")),
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
            if (scheduler.nextScheduledTrigger() == null) ReliabilityStatus.WARNING else ReliabilityStatus.OK,
            R.string.reliability_next_alarm_explanation,
            scheduler.nextScheduledTrigger(),
        ),
    )

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
