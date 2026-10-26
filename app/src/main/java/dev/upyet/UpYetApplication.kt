package dev.upyet

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.upyet.core.logging.AlarmLog
import dev.upyet.core.logging.DiagnosticLogStore
import dev.upyet.core.notifications.AlarmNotifications
import javax.inject.Inject

@HiltAndroidApp
class UpYetApplication : Application() {
    // Its only dependency is the application Context, so this injection builds no Room and no DataStore and is
    // safe on the Direct Boot launch that precedes first unlock (AGENTS.md §11, §14).
    @Inject lateinit var notifications: AlarmNotifications

    override fun onCreate() {
        super.onCreate()
        // Use device-protected storage because AlarmLog is called from directBootAware components before the
        // user has unlocked, and that window is exactly where "the alarm never rang" lives. See docs/adr/0004.
        AlarmLog.install(DiagnosticLogStore.forApp(this))
        // Eager, so system settings lists every channel on a fresh install, before anything has rung (ADR 0012).
        notifications.ensureChannels()
    }
}
