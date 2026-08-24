package dev.myalarm.alarm.domain

import java.time.DayOfWeek

sealed interface Recurrence {
    data object OneTime : Recurrence

    data object Daily : Recurrence

    data class Weekly(val days: Set<DayOfWeek>) : Recurrence {
        init {
            require(days.isNotEmpty()) {
                "Weekly recurrence must contain at least one day"
            }
        }
    }
}
