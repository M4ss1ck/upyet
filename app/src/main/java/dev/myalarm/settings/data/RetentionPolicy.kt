package dev.myalarm.settings.data

enum class RetentionPolicy(val days: Int?) {
    ONE_DAY(1),
    SEVEN_DAYS(7),
    THIRTY_DAYS(30),
    FOREVER(null),
}
