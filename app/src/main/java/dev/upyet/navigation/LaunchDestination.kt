package dev.upyet.navigation

/**
 * Where the main activity opens when the trampoline asks for a screen. Pure data with a stable string encoding, so
 * it travels in an intent extra and is JVM-testable. A launcher start carries no destination at all.
 */
sealed interface LaunchDestination {
    data object Alarms : LaunchDestination

    data object Timers : LaunchDestination

    data object Reliability : LaunchDestination

    /** [prefill] is an `AlarmPrefillCodec` token, or null for a blank new alarm. */
    data class AlarmEditor(val prefill: String?) : LaunchDestination
}

const val EXTRA_DESTINATION = "dev.upyet.extra.DESTINATION"
const val EXTRA_ALARM_PREFILL = "dev.upyet.extra.ALARM_PREFILL"

// Wire names: stored in intents, so they never change with a class rename.
private const val NAME_ALARMS = "alarms"
private const val NAME_TIMERS = "timers"
private const val NAME_RELIABILITY = "reliability"
private const val NAME_ALARM_EDITOR = "alarm_editor"

/** The extras that carry this destination: [EXTRA_DESTINATION] always, [EXTRA_ALARM_PREFILL] only for the editor. */
fun LaunchDestination.toExtras(): Map<String, String?> = when (this) {
    LaunchDestination.Alarms -> mapOf(EXTRA_DESTINATION to NAME_ALARMS)
    LaunchDestination.Timers -> mapOf(EXTRA_DESTINATION to NAME_TIMERS)
    LaunchDestination.Reliability -> mapOf(EXTRA_DESTINATION to NAME_RELIABILITY)
    is LaunchDestination.AlarmEditor -> mapOf(EXTRA_DESTINATION to NAME_ALARM_EDITOR, EXTRA_ALARM_PREFILL to prefill)
}

/** Null for an absent or unknown [name], so a launcher intent or a stale extra opens the default screen. */
fun decodeLaunchDestination(name: String?, prefill: String?): LaunchDestination? = when (name) {
    NAME_ALARMS -> LaunchDestination.Alarms
    NAME_TIMERS -> LaunchDestination.Timers
    NAME_RELIABILITY -> LaunchDestination.Reliability
    NAME_ALARM_EDITOR -> LaunchDestination.AlarmEditor(prefill)
    else -> null
}
