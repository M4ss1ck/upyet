package dev.upyet.core.logging

import android.util.Log

/** Structured alarm logging; labels, file names, and video content must never be logged. */
object AlarmLog {
    fun event(name: String, vararg pairs: Pair<String, Any?>) {
        val fields = pairs.joinToString(",") { (key, value) -> "$key=$value" }
        Log.i("UpYet", if (fields.isEmpty()) name else "$name $fields")
    }
}
