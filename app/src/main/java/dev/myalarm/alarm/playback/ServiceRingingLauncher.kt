package dev.myalarm.alarm.playback

import android.content.Context
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.OccurrenceId
import dev.myalarm.alarm.scheduling.AlarmOccurrenceKind
import dev.myalarm.alarm.scheduling.RingingLauncher
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
