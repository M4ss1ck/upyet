package dev.myalarm.alarm.scheduling

enum class AlarmOccurrenceKind(val requestCodeBit: Int) {
    MAIN(0),
    SNOOZE(1),
}
