package dev.myalarm.alarm.data

import dev.myalarm.alarm.domain.Alarm
import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.Recurrence
import dev.myalarm.core.database.AlarmEntity
import java.time.DayOfWeek
import java.time.LocalTime

private const val ONE_TIME = "ONE_TIME"
private const val DAILY = "DAILY"
private const val WEEKLY = "WEEKLY"

fun AlarmEntity.toDomain(): Alarm = Alarm(
    id = AlarmId(id),
    time =
    LocalTime.of(
        minuteOfDay / 60,
        minuteOfDay % 60,
    ),
    enabled = enabled,
    label = label,
    recurrence =
    decodeRecurrence(
        recurrenceType,
        weekdayMask,
    ),
    soundUri = soundUri,
    vibrationEnabled = vibrationEnabled,
    snoozeMinutes = snoozeMinutes,
    evidenceEnabled = evidenceEnabled,
    createdAt =
    java.time.Instant.ofEpochMilli(
        createdAt,
    ),
    updatedAt =
    java.time.Instant.ofEpochMilli(
        updatedAt,
    ),
)

fun Alarm.toEntity(): AlarmEntity {
    val (type, mask) = encodeRecurrence(recurrence)
    return AlarmEntity(
        id = id.value,
        minuteOfDay = time.hour * 60 + time.minute,
        enabled = enabled,
        label = label,
        recurrenceType = type,
        weekdayMask = mask,
        soundUri = soundUri,
        vibrationEnabled = vibrationEnabled,
        snoozeMinutes = snoozeMinutes,
        evidenceEnabled = evidenceEnabled,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
    )
}

fun encodeRecurrence(recurrence: Recurrence): Pair<String, Int> = when (recurrence) {
    Recurrence.OneTime -> {
        ONE_TIME to 0
    }

    Recurrence.Daily -> {
        DAILY to 0
    }

    is Recurrence.Weekly -> {
        WEEKLY to
            recurrence.days.fold(0) { mask, day ->
                mask or
                    (1 shl (day.value - 1))
            }
    }
}

fun decodeRecurrence(type: String, mask: Int): Recurrence = when (type) {
    ONE_TIME -> {
        Recurrence.OneTime
    }

    DAILY -> {
        Recurrence.Daily
    }

    WEEKLY -> {
        Recurrence.Weekly(
            DayOfWeek.entries
                .filter {
                    mask and
                        (1 shl (it.value - 1)) !=
                        0
                }.toSet(),
        )
    }

    else -> {
        error("Unknown recurrence type: $type")
    }
}
