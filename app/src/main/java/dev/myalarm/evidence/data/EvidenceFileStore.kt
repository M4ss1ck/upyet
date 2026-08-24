package dev.myalarm.evidence.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface EvidenceFileStore {
    fun newEvidenceFile(): File
    fun resolve(fileName: String): File
    fun delete(fileName: String): Boolean
    fun deleteAll(fileNames: Iterable<String>)
}

@Singleton
class AppEvidenceFileStore @Inject constructor(@ApplicationContext private val context: Context) : EvidenceFileStore {
    private val evidenceDirectory: File
        get() =
            File(
                context.noBackupFilesDir,
                "evidence",
            ).also {
                it.mkdirs()
            }

    override fun newEvidenceFile(): File = File(
        evidenceDirectory,
        "${UUID.randomUUID()}.mp4",
    )

    override fun resolve(fileName: String): File = File(evidenceDirectory, fileName)

    override fun delete(fileName: String): Boolean = resolve(fileName).delete()

    override fun deleteAll(fileNames: Iterable<String>) {
        fileNames.forEach(::delete)
    }
}
