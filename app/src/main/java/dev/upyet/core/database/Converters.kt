package dev.upyet.core.database

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalTime

class Converters {
    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localTimeToInt(value: LocalTime?): Int? = value?.let {
        it.hour *
            60 +
            it.minute
    }

    @TypeConverter
    fun intToLocalTime(value: Int?): LocalTime? = value?.let {
        LocalTime.of(it / 60, it % 60)
    }
}
