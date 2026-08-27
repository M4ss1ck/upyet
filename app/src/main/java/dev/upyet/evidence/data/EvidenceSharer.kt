package dev.upyet.evidence.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.evidence.domain.ShareableClip
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EvidenceSharer @Inject constructor(@ApplicationContext private val context: Context, private val files: EvidenceFileStore) {
    fun shareIntent(clips: List<ShareableClip>, summary: String): Intent? {
        if (clips.isEmpty()) return null

        val hasAnySource = clips.any { files.resolve(it.fileName).exists() }
        if (!hasAnySource) return null

        val sharedDir = File(context.cacheDir, "shared")
        // Clean stale copies from previous shares exactly here and nowhere else.
        if (sharedDir.exists()) {
            sharedDir.listFiles()?.forEach { it.delete() }
        }
        sharedDir.mkdirs()

        val uris = mutableListOf<Uri>()
        for (clip in clips) {
            val source = files.resolve(clip.fileName)
            if (!source.exists()) continue
            val dest = File(sharedDir, clip.outgoingName)
            source.copyTo(dest, overwrite = true)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.shared", dest)
            uris += uri
        }

        if (uris.isEmpty()) return null

        val intent = Intent().apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_TEXT, summary)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (uris.size == 1) {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                action = Intent.ACTION_SEND_MULTIPLE
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        return Intent.createChooser(intent, null)
    }
}
