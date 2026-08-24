package dev.myalarm.evidence.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EvidenceFileStore
@Inject
constructor(@ApplicationContext private val context: Context) {
    private val evidenceDirectory: File
        get() =
            File(
                context.noBackupFilesDir,
                "evidence",
            ).also {
                it.mkdirs()
            }

    fun newEvidenceFile(): File = File(
        evidenceDirectory,
        "${UUID.randomUUID()}.mp4",
    )

    fun resolve(fileName: String): File = File(evidenceDirectory, fileName)

    fun delete(fileName: String): Boolean = resolve(fileName).delete()

    fun deleteAll(fileNames: Iterable<String>) {
        fileNames.forEach(::delete)
    }
}
