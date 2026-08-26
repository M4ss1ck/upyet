package dev.upyet.e2e

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import dev.upyet.R
import dev.upyet.core.database.AlarmOccurrenceEntity
import dev.upyet.core.database.EvidenceSegmentEntity
import dev.upyet.core.database.UpYetDatabase
import dev.upyet.evidence.domain.EvidenceStatus
import dev.upyet.evidence.domain.OccurrenceOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The `core-path` scenario of the device matrix in `docs/TESTING.md`, driven end to end:
 * schedule -> screen off -> the alarm fires -> the ringing screen comes up in front -> the front camera
 * records -> dismiss -> a finalized segment and a real file are on disk.
 *
 * What this test CANNOT prove, and never will: that the frame the camera captured shows an actual,
 * recognisable person. It asserts that a non-empty file exists, that CameraX finalized it, and that it
 * carries a real duration. Whether the video answers "did I actually wake up and dismiss that alarm?" is
 * a human-eyes judgement forever, which is why `history-thumbnail` stays in the human-eyes lane and why
 * a green run here is never written up as evidence that evidence recording works.
 *
 * Two further limits, reported rather than silently skipped:
 *  - The keyguard claim ("the ringing screen appears ABOVE the lock screen") is only asserted when the
 *    device actually has a secure lock screen. Without a PIN there is no keyguard to be above; that is
 *    why `core-path` is marked `physical` in the matrix even though this test runs anywhere.
 *  - This is the debug package. The release package has no debug receiver, so the release pass is manual.
 */
@RunWith(AndroidJUnit4::class)
class CorePathFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device: UiDevice = UiDevice.getInstance(instrumentation)
    private val keyguard by lazy { context.getSystemService(KeyguardManager::class.java) }

    /**
     * A read-only view of the database the running app is writing to. A Hilt entry point cannot be used
     * from here: the androidTest source set is processed into its own Hilt component, so the application's
     * already-generated component does not implement anything declared in the test APK.
     */
    private val database: UpYetDatabase by lazy {
        Room.databaseBuilder(context, UpYetDatabase::class.java, DATABASE_NAME).build()
    }

    /** Mirrors AppEvidenceFileStore's layout, which is not reachable from here for the same Hilt reason. */
    private val evidenceDirectory: File by lazy { File(context.noBackupFilesDir, "evidence") }

    @Before fun grantPermissionsAndWake() {
        grant(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) grant(Manifest.permission.POST_NOTIFICATIONS)
        device.wakeUp()
        shell("wm dismiss-keyguard")
        device.pressHome()
    }

    @After fun leaveTheDeviceUsable() {
        device.wakeUp()
        shell("wm dismiss-keyguard")
        // Never force-stop this package here: instrumentation shares the application process and would die
        // with it. A failed run can leave the alarm ringing, so dismiss it the way a user would.
        if (focusedWindow().contains(RINGING_ACTIVITY)) {
            device.findObject(By.desc(context.getString(R.string.dismiss_alarm)))?.click()
        }
        device.pressHome()
        database.close()
    }

    @Test fun alarmRingsAboveTheLockScreenRecordsEvidenceAndDismisses() {
        val occurrencesBefore = occurrences().map { it.id }.toSet()

        // Literally the recipe from docs/TESTING.md §5, run as the shell. Not sent in-process: the debug
        // receiver is guarded by DUMP, which the shell holds and the app's own uid does not - and driving
        // the documented command means a rotted recipe fails here instead of during a manual pass.
        shell("am broadcast -a $ACTION_SCHEDULE --ei seconds $LEAD_SECONDS --ez evidence true -p ${context.packageName}")

        // Screen off before the alarm fires: waking the screen is part of what is being verified.
        device.sleep()
        await("the screen to be off") { !device.isScreenOn }

        // The focused window is the platform's own answer to "what is in front", which no UI query can talk
        // it out of. Reaching it means the alarm woke the screen and our Activity took the foreground.
        await("$RINGING_ACTIVITY to become the focused window", RING_TIMEOUT_MILLIS) {
            focusedWindow().contains(RINGING_ACTIVITY)
        }
        if (keyguard?.isKeyguardSecure == true) {
            assertThat(keyguard?.isKeyguardLocked).isTrue()
        } else {
            Log.i(TAG, "e2e_keyguard_claim_not_exercised reason=no_secure_lock_screen")
        }
        assertThat(device.wait(Until.hasObject(By.pkg(context.packageName)), UI_TIMEOUT_MILLIS)).isTrue()

        // The pulsing dot is only emitted once CameraX has reported Start, so its content description is
        // the UI's own statement that recording actually began - not that the camera was merely bound.
        val recordingIndicator = context.getString(R.string.evidence_recording_indicator)
        assertThat(device.wait(Until.hasObject(By.desc(recordingIndicator)), CAMERA_TIMEOUT_MILLIS)).isTrue()

        captureScreenshot("core-path-ringing")

        // Wait on footage, not on the clock: the elapsed counter is driven by the bytes CameraX has
        // actually written, and dismissing before there are any produces an empty file and a FAILED segment.
        await("the recording timer to reach ${MIN_RECORDED_SECONDS}s", CAMERA_TIMEOUT_MILLIS) {
            elapsedRecordingSeconds() >= MIN_RECORDED_SECONDS
        }

        val dismiss = device.findObject(By.desc(context.getString(R.string.dismiss_alarm)))
        assertThat(dismiss).isNotNull()
        dismiss.click()

        await("the ringing window to go away", UI_TIMEOUT_MILLIS) { !focusedWindow().contains(RINGING_ACTIVITY) }

        val occurrence = awaitValue("a new occurrence to be recorded", PERSISTENCE_TIMEOUT_MILLIS) {
            occurrences().firstOrNull { it.id !in occurrencesBefore }
        }
        assertThat(occurrence.outcome).isEqualTo(OccurrenceOutcome.DISMISSED.name)
        assertThat(occurrence.triggeredAt).isNotNull()
        assertThat(occurrence.activityVisibleAt).isNotNull()
        assertThat(occurrence.dismissedAt).isNotNull()

        // Finalization is asynchronous by design: dismissal never waits for the camera.
        val segment = awaitValue("the evidence segment to be finalized", PERSISTENCE_TIMEOUT_MILLIS) {
            segments(occurrence.id).firstOrNull { it.finalizedAt != null }
        }
        // PARTIAL is accepted alongside RECORDED because unbinding the camera as the Activity stops can
        // reach CameraX before the stop() from dismiss does, which it reports as SOURCE_INACTIVE. Both mean
        // real footage was written; the duration and size assertions below are what rule out an empty
        // capture, and every FAILED status still fails this test.
        assertThat(segment.status).isIn(listOf(EvidenceStatus.RECORDED.name, EvidenceStatus.PARTIAL.name))
        assertThat(segment.startedAt).isNotNull()
        assertThat(segment.durationMs ?: 0L).isAtLeast(MIN_RECORDED_SECONDS * MILLIS_PER_SECOND)

        val file = File(evidenceDirectory, requireNotNull(segment.fileName))
        assertThat(file.isFile).isTrue()
        assertThat(file.length()).isGreaterThan(0L)
    }

    private fun occurrences(): List<AlarmOccurrenceEntity> = runBlocking { database.occurrenceDao().observeAll().first() }

    private fun segments(occurrenceId: Long): List<EvidenceSegmentEntity> = runBlocking {
        database.evidenceSegmentDao().observeForOccurrence(occurrenceId).first()
    }

    /**
     * Seconds shown by the recording chip, or -1 when it is not showing a running recording. The label is
     * read back from resources so the flow survives translation; only the `m:ss` tail is parsed.
     */
    private fun elapsedRecordingSeconds(): Int {
        val chip = device.findObject(By.textStartsWith(context.getString(R.string.evidence_recording))) ?: return -1
        val match = ELAPSED_PATTERN.find(chip.text.orEmpty()) ?: return -1
        val (minutes, seconds) = match.destructured
        return minutes.toInt() * SECONDS_PER_MINUTE + seconds.toInt()
    }

    /** `mCurrentFocus` is the platform's record of the front-most window, keyguard included. */
    private fun focusedWindow(): String = shell("dumpsys window").lineSequence().firstOrNull { it.contains("mCurrentFocus") }.orEmpty()

    private fun captureScreenshot(name: String) {
        val directory = File(context.getExternalFilesDir(null), "e2e").also { it.mkdirs() }
        val file = File(directory, "$name.png")
        val captured = device.takeScreenshot(file)
        // Logged, not asserted: a missing screenshot is a lost diagnostic, not a product failure. Pull it
        // with `adb pull` at the path this line reports.
        Log.i(TAG, "e2e_screenshot ok=$captured path=${file.absolutePath}")
    }

    private fun grant(permission: String) {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
    }

    private fun shell(command: String): String = device.executeShellCommand(command)

    /** Polls instead of sleeping, and says what it was waiting for when it gives up. */
    private fun await(what: String, timeoutMillis: Long = UI_TIMEOUT_MILLIS, condition: () -> Boolean) {
        awaitValue(what, timeoutMillis) { if (condition()) Unit else null }
    }

    /** Polls until [produce] yields a value and returns it, so the waited-for thing is the assertion subject. */
    private fun <T : Any> awaitValue(what: String, timeoutMillis: Long, produce: () -> T?): T {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            produce()?.let { return it }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("Timed out after ${timeoutMillis}ms waiting for $what")
    }

    private companion object {
        const val TAG = "UpYet"
        const val DATABASE_NAME = "upyet.db"
        const val ACTION_SCHEDULE = "dev.upyet.debug.SCHEDULE"
        const val RINGING_ACTIVITY = "RingingActivity"
        const val LEAD_SECONDS = 20
        const val MIN_RECORDED_SECONDS = 3
        const val SECONDS_PER_MINUTE = 60
        const val MILLIS_PER_SECOND = 1_000L
        const val POLL_MILLIS = 250L
        const val UI_TIMEOUT_MILLIS = 15_000L
        const val RING_TIMEOUT_MILLIS = 90_000L
        const val CAMERA_TIMEOUT_MILLIS = 30_000L
        const val PERSISTENCE_TIMEOUT_MILLIS = 20_000L
        val ELAPSED_PATTERN = Regex("(\\d+):(\\d{2})")
    }
}
