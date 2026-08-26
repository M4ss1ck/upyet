package dev.upyet.evidence.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface EvidenceFileStore {
    fun newEvidenceFile(): File
    fun resolve(fileName: String): File

    /** The history thumbnail that belongs to [fileName]: same directory, same opaque basename, `.jpg`. */
    fun resolveThumbnail(fileName: String): File
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

    override fun resolveThumbnail(fileName: String): File = File(evidenceDirectory, "${fileName.substringBeforeLast('.')}.jpg")

    /**
     * The thumbnail is evidence too, so it dies with its clip; folding that in here means no call site
     * can forget it. The return value still describes the clip - that is what callers act on.
     */
    override fun delete(fileName: String): Boolean {
        resolveThumbnail(fileName).delete()
        return resolve(fileName).delete()
    }

    override fun deleteAll(fileNames: Iterable<String>) {
        fileNames.forEach(::delete)
    }
}
