package dev.upyet.alarm.playback

import android.content.Context
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.alarm.scheduling.AlarmOccurrenceKind
import dev.upyet.alarm.scheduling.RingingLauncher
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceRingingLauncher @Inject constructor(@ApplicationContext private val context: Context) : RingingLauncher {
    override fun launch(alarmId: AlarmId, scheduledFor: Instant, kind: AlarmOccurrenceKind, parentOccurrenceId: OccurrenceId?) {
        ContextCompat.startForegroundService(
            context,
            AlarmPlaybackService.startIntent(context, alarmId, scheduledFor, kind, parentOccurrenceId),
        )
    }
}
