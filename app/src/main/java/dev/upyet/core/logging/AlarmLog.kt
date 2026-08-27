package dev.upyet.core.logging

import android.util.Log
import java.time.Instant

/** Structured alarm logging; labels, file names, and video content must never be logged. */
object AlarmLog {
    /**
     * Keys whose values never reach either logcat or the persisted log. Redaction happens here, at the
     * point of writing, so there is no window in which a redactable value exists in the file - and it does
     * not depend on the build type, because the file exists to be shared. See `docs/adr/0005`. Alarm ids
     * and wall-clock trigger times are deliberately not on this list: an id is what lets a developer
     * follow one alarm through a log, and the label is the part that is sensitive.
     */
    private val redactedKeys = setOf("label", "filename", "path", "uri", "file")

    @Volatile private var store: DiagnosticLogStore? = null

    fun install(store: DiagnosticLogStore) {
        this.store = store
    }

    fun event(name: String, vararg pairs: Pair<String, Any?>) {
        val fields = pairs.joinToString(",") { (key, value) ->
            if (key.lowercase() in redactedKeys) "$key=redacted" else "$key=$value"
        }
        val message = if (fields.isEmpty()) name else "$name $fields"
        Log.i("UpYet", message)
        store?.append("${Instant.now()} $message")
    }
}
