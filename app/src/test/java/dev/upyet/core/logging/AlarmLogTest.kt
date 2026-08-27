package dev.upyet.core.logging

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class AlarmLogTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun eventWithNoPairsStoresTimestampAndName() {
        val dir = temporaryFolder.newFolder()
        val store = DiagnosticLogStore(dir)
        AlarmLog.install(store)

        AlarmLog.event("alarm_triggered")

        val line = store.read().trim()
        assertThat(line).endsWith("alarm_triggered")
        val timestampPart = line.substringBefore(" ")
        // Instant.parse will throw if the timestamp is malformed.
        Instant.parse(timestampPart)
    }

    @Test
    fun eventWithPairsStoresKeyValuePairs() {
        val dir = temporaryFolder.newFolder()
        val store = DiagnosticLogStore(dir)
        AlarmLog.install(store)

        AlarmLog.event("alarm_triggered", "alarmId" to 7, "kind" to "SNOOZE")

        val line = store.read().trim()
        assertThat(line).contains("alarm_triggered")
        assertThat(line).contains("alarmId=7")
        assertThat(line).contains("kind=SNOOZE")
    }

    @Test
    fun redactedKeysAreStoredAsRedactedWhileOtherKeysPassThrough() {
        val dir = temporaryFolder.newFolder()
        val store = DiagnosticLogStore(dir)
        AlarmLog.install(store)

        AlarmLog.event(
            "alarm_triggered",
            "label" to "My Label",
            "fileName" to "secret.mp4",
            "path" to "/private/path",
            "uri" to "content://secret",
            "file" to "hidden",
            "alarmId" to 7,
        )

        val line = store.read().trim()
        assertThat(line).contains("label=redacted")
        assertThat(line).contains("fileName=redacted")
        assertThat(line).contains("path=redacted")
        assertThat(line).contains("uri=redacted")
        assertThat(line).contains("file=redacted")
        assertThat(line).contains("alarmId=7")
        assertThat(line).doesNotContain("My Label")
        assertThat(line).doesNotContain("secret.mp4")
        assertThat(line).doesNotContain("/private/path")
        assertThat(line).doesNotContain("content://secret")
        assertThat(line).doesNotContain("hidden")
    }

    /**
     * The store is on the ringing path, so a store that cannot write must be as harmless as no store at
     * all. A regular file where the directory should be is the cheapest way to make every write fail.
     */
    @Test
    fun eventDoesNotThrowWhenTheStoreCannotWrite() {
        AlarmLog.install(DiagnosticLogStore(temporaryFolder.newFile()))

        AlarmLog.event("alarm_triggered", "alarmId" to 1)
    }
}
