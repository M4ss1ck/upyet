package dev.upyet.settings.data

import java.time.Instant

enum class RetentionPolicy(val days: Int?) {
    ONE_DAY(1),
    SEVEN_DAYS(7),
    THIRTY_DAYS(30),
    FOREVER(null),
}

fun RetentionPolicy.cutoffFrom(now: Instant): Instant? = days?.let { now.minusSeconds(it * 86_400L) }
