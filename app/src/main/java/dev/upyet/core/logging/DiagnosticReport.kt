package dev.upyet.core.logging

import java.time.Instant

data class DiagnosticContext(
    val appVersion: String,
    val versionCode: Int,
    val androidRelease: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val locale: String,
    val checks: List<Pair<String, String>>,
    val settings: List<Pair<String, String>>,
)

fun diagnosticReport(context: DiagnosticContext, generatedAt: Instant, log: String): String = buildString {
    append("UpYet diagnostic report\n")
    append("Generated: $generatedAt\n")
    append("\n")
    append("App: ${context.appVersion} (${context.versionCode})\n")
    append("Android: ${context.androidRelease} (SDK ${context.sdkInt})\n")
    append("Device: ${context.manufacturer} ${context.model}\n")
    append("Locale: ${context.locale}\n")
    if (context.checks.isNotEmpty()) {
        append("\n")
        append("Reliability checks\n")
        for ((id, status) in context.checks) {
            append("  $id: $status\n")
        }
    }
    if (context.settings.isNotEmpty()) {
        append("\n")
        append("Settings\n")
        for ((name, value) in context.settings) {
            append("  $name: $value\n")
        }
    }
    append("\n")
    append("Log, oldest first\n")
    if (log.isBlank()) {
        append("No events recorded.\n")
    } else {
        append(log)
        if (!log.endsWith("\n")) append("\n")
    }
}

fun diagnosticReportSummary(context: DiagnosticContext): String =
    "UpYet ${context.appVersion} diagnostic report - Android ${context.androidRelease} (SDK ${context.sdkInt}) " +
        "on ${context.manufacturer} ${context.model}. Full log attached."
