package dev.upyet.core.logging

import java.io.File
import java.io.FileOutputStream

/**
 * Append-only, size-bounded record of [AlarmLog] events, kept so a user whose alarm did not ring has
 * something to send. Two files of [MAX_BYTES] bound it: rotation is by size and not by age, because the
 * person reporting last Tuesday is exactly the one whose evidence retention already reclaimed.
 *
 * Writes are synchronous on the caller's thread, and both methods swallow their I/O failures: this is
 * written on the alarm ringing path, and failing to record that the alarm rang must never stop it
 * ringing. See `docs/adr/0004`.
 */
class DiagnosticLogStore(private val directory: File) {
    private val current: File get() = File(directory, CURRENT)
    private val previous: File get() = File(directory, PREVIOUS)

    fun append(line: String) {
        runCatching {
            directory.mkdirs()
            val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
            if (current.isFile && current.length() + bytes.size > MAX_BYTES) {
                previous.delete()
                current.renameTo(previous)
            }
            FileOutputStream(current, true).use { it.write(bytes) }
        }
    }

    /** Everything still kept, oldest line first. */
    fun read(): String = runCatching {
        listOf(previous, current).filter { it.isFile }.joinToString("") { it.readText(Charsets.UTF_8) }
    }.getOrDefault("")

    companion object {
        const val MAX_BYTES = 128 * 1024
        const val CURRENT = "diagnostics.log"
        const val PREVIOUS = "diagnostics-previous.log"
    }
}
