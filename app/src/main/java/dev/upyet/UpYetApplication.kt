package dev.upyet

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.upyet.core.logging.AlarmLog
import dev.upyet.core.logging.DiagnosticLogStore

@HiltAndroidApp
class UpYetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Use device-protected storage because AlarmLog is called from directBootAware components before the
        // user has unlocked, and that window is exactly where "the alarm never rang" lives. See docs/adr/0004.
        AlarmLog.install(DiagnosticLogStore.forApp(this))
    }
}
