package dev.upyet.core.logging

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticReportSharer @Inject constructor(@ApplicationContext private val context: Context) {
    fun shareIntent(report: String, summary: String, generatedAt: Instant, zone: ZoneId): Intent {
        // It writes into File(context.cacheDir, "diagnostics"), NOT into cacheDir/shared. This matters:
        // EvidenceSharer wipes cacheDir/shared on every share, and a shared directory would let an evidence
        // share delete a diagnostic report out from under a user mid-share.
        val diagnosticsDir = File(context.cacheDir, "diagnostics")
        if (diagnosticsDir.exists()) {
            diagnosticsDir.listFiles()?.forEach { it.delete() }
        }
        diagnosticsDir.mkdirs()

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm", Locale.ROOT).withZone(zone)
        val fileName = "upyet-diagnostics-${formatter.format(generatedAt)}.txt"
        val file = File(diagnosticsDir, fileName)
        file.writeText(report, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.shared", file)
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, summary)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, null)
    }
}
