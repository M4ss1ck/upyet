package dev.upyet.timer.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * AGENTS.md §8a and ADR 0012 as source rules: timer playback depends on no Compose, Room, camera or evidence
 * code, reuses no alarm ringing object, and never cancels the upcoming-alarm notification.
 */
class TimerPlaybackIsolationTest {
    // Gradle runs unit tests with the module directory as the working directory.
    private val sources = File("src/main/java/dev/upyet/timer/playback")
        .listFiles { file -> file.extension == "kt" }
        .orEmpty()
        .associate { it.name to it.readText() }

    @Test
    fun thePackageExists() {
        assertThat(sources.keys).containsAtLeast("TimerPlaybackService.kt", "TimerRinging.kt", "TimerNotifications.kt")
    }

    @Test
    fun itImportsNoComposeRoomCameraOrEvidence() {
        val forbidden = listOf("androidx.compose", "androidx.room", "androidx.camera", "dev.upyet.evidence", "dev.upyet.core.database")
        val hits = sources.flatMap { (name, text) ->
            text.lines().filter { line -> line.startsWith("import ") && forbidden.any { line.contains(it) } }.map { "$name: $it" }
        }
        assertThat(hits).isEmpty()
    }

    @Test
    fun itSharesOnlyTheSoundPlayerAndVibratorWithTheAlarmPath() {
        val alarmImports = sources.values
            .flatMap { it.lines() }
            .filter { it.startsWith("import dev.upyet.alarm.") }
            .toSet()
        assertThat(alarmImports).containsExactly(
            "import dev.upyet.alarm.playback.AlarmSoundPlayer",
            "import dev.upyet.alarm.playback.AlarmVibrator",
        )
    }

    @Test
    fun itNeverCancelsTheUpcomingAlarmNotification() {
        // Code lines only: the service's KDoc names the call in order to say it is never made.
        val calls = sources.filterValues { text ->
            text.lines().map { it.trim() }.any { line -> !line.startsWith("*") && !line.startsWith("//") && "cancelUpcoming(" in line }
        }
        assertThat(calls.keys).isEmpty()
    }
}
