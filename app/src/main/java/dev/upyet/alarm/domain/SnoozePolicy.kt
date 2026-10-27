package dev.upyet.alarm.domain

/**
 * How an alarm or the default for new alarms handles snooze. [Off] means ringing has no snooze action at all
 * (GLOSSARY.md, No snooze); [After] snoozes for [minutes].
 */
sealed interface SnoozePolicy {
    data object Off : SnoozePolicy

    data class After(val minutes: Int) : SnoozePolicy {
        init {
            require(minutes > 0) { "Snooze minutes must be positive, was $minutes" }
        }
    }

    companion object {
        /** Off in the Int encodings shared by DataStore and the Direct Boot mirror (GLOSSARY.md, No snooze). */
        const val OFF_ENCODED = 0

        fun decode(value: Int): SnoozePolicy = if (value <= OFF_ENCODED) Off else After(value)
    }
}

fun SnoozePolicy.encode(): Int = when (this) {
    SnoozePolicy.Off -> SnoozePolicy.OFF_ENCODED
    is SnoozePolicy.After -> minutes
}

/** The one rule for whether a ring offers snooze at all: the ringing screen, the notification and the service all read it. */
val SnoozePolicy.allowsSnooze: Boolean
    get() = when (this) {
        SnoozePolicy.Off -> false
        is SnoozePolicy.After -> true
    }
