package dev.upyet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.upyet.alarm.scheduling.AlarmRescheduler
import dev.upyet.core.logging.AlarmLog
import dev.upyet.evidence.data.RetentionCleaner
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Runs the once-per-launch housekeeping. It lives in a ViewModel so configuration changes do not repeat it,
 * and it is deliberately not a background worker: retention is cheap and only matters while the app is used.
 */
@HiltViewModel
class MainViewModel
@Inject
constructor(private val retentionCleaner: RetentionCleaner, private val rescheduler: AlarmRescheduler) :
    ViewModel() {
    init {
        viewModelScope.launch {
            val removed = retentionCleaner.clean()
            AlarmLog.event("retention_cleanup", "removed" to removed)
            // Launching is the one moment the upcoming-alarm notification can be recomputed after a
            // force stop, which the platform does not announce.
            rescheduler.refreshUpcoming()
        }
    }
}
