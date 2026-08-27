package dev.upyet.core.logging

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class DiagnosticReportTest {
    private val context = DiagnosticContext(
        appVersion = "0.1.6",
        versionCode = 10106,
        androidRelease = "16",
        sdkInt = 36,
        manufacturer = "Xiaomi",
        model = "24072PX77G",
        locale = "en-GB",
        checks = listOf("exact" to "OK", "notifications" to "OK", "alarm_volume" to "WARNING"),
        settings = listOf("retention" to "SEVEN_DAYS", "maxSnoozes" to "3"),
    )
    private val instant = Instant.parse("2026-08-27T15:41:02Z")

    @Test fun fullReportRendersEveryBlockInOrder() {
        val log = "2026-08-27T10:00:00Z alarm_scheduled id=1\n2026-08-27T10:01:00Z alarm_triggered id=1"
        val report = diagnosticReport(context, instant, log)
        val expected = """
            UpYet diagnostic report
            Generated: 2026-08-27T15:41:02Z

            App: 0.1.6 (10106)
            Android: 16 (SDK 36)
            Device: Xiaomi 24072PX77G
            Locale: en-GB

            Reliability checks
              exact: OK
              notifications: OK
              alarm_volume: WARNING

            Settings
              retention: SEVEN_DAYS
              maxSnoozes: 3

            Log, oldest first
            2026-08-27T10:00:00Z alarm_scheduled id=1
            2026-08-27T10:01:00Z alarm_triggered id=1
        """.trimIndent() + "\n"
        assertThat(report).isEqualTo(expected)
    }

    @Test fun blankLogRendersNoEventsRecorded() {
        val report = diagnosticReport(context, instant, "")
        assertThat(report).contains("Log, oldest first\nNo events recorded.\n")
        assertThat(report).doesNotContain("Log, oldest first\n\n")
        // Also check blank with whitespace
        val report2 = diagnosticReport(context, instant, "   \n  ")
        assertThat(report2).contains("No events recorded.")
    }

    @Test fun emptyChecksAndSettingsOmitHeadings() {
        val empty = context.copy(checks = emptyList(), settings = emptyList())
        val report = diagnosticReport(empty, instant, "some log")
        assertThat(report).doesNotContain("Reliability checks")
        assertThat(report).doesNotContain("Settings")
        assertThat(report).contains("App: 0.1.6 (10106)")
        assertThat(report).contains("Log, oldest first")

        val onlyChecks = context.copy(settings = emptyList())
        val report2 = diagnosticReport(onlyChecks, instant, "some log")
        assertThat(report2).contains("Reliability checks")
        assertThat(report2).doesNotContain("\nSettings\n")

        val onlySettings = context.copy(checks = emptyList())
        val report3 = diagnosticReport(onlySettings, instant, "some log")
        assertThat(report3).doesNotContain("Reliability checks")
        assertThat(report3).contains("Settings")
    }

    @Test fun reportEndsWithExactlyOneNewline() {
        val report = diagnosticReport(context, instant, "log line")
        assertThat(report.endsWith("\n")).isTrue()
        assertThat(report.endsWith("\n\n")).isFalse()

        val blankReport = diagnosticReport(context, instant, "")
        assertThat(blankReport.endsWith("\n")).isTrue()
        assertThat(blankReport.endsWith("\n\n")).isFalse()

        val withTrailingNewline = diagnosticReport(context, instant, "line1\nline2\n")
        assertThat(withTrailingNewline.endsWith("\n")).isTrue()
        assertThat(withTrailingNewline.endsWith("\n\n")).isFalse()
    }

    @Test fun summaryIsSingleLineWithVersionAndDevice() {
        val summary = diagnosticReportSummary(context)
        assertThat(summary).isEqualTo(
            "UpYet 0.1.6 diagnostic report - Android 16 (SDK 36) on Xiaomi 24072PX77G. Full log attached.",
        )
        assertThat(summary).doesNotContain("\n")
    }
}
