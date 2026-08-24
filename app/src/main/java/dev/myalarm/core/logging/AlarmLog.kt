package dev.myalarm.core.logging

import android.util.Log

/** Structured alarm logging; labels, file names, and video content must never be logged. */
object AlarmLog {
    fun event(name: String, vararg pairs: Pair<String, Any?>) {
        val fields = pairs.joinToString(",") { (key, value) -> "$key=$value" }
        Log.i("MyAlarm", if (fields.isEmpty()) name else "$name $fields")
    }
}
