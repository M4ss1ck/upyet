package dev.upyet.worldclock.domain

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Where the sun stands overhead: [latitude] is the declination, [longitude] is in [-180, 180), both in degrees. */
data class SubsolarPoint(val latitude: Double, val longitude: Double)

/**
 * The night half of the planet, computed from the subsolar point rather than from a timezone table: "is it dark
 * there" is the same question as "is the sun below that horizon".
 */
fun subsolarPoint(instant: Instant): SubsolarPoint {
    val utc = instant.atZone(ZoneOffset.UTC)
    // A one-term yearly cosine is accurate to well within what a world map can show, and needs no ephemeris table.
    val declination = -23.44 * cos(2.0 * PI * (utc.dayOfYear + 10) / 365.0)
    // The sun stands overhead at 15 degrees west for every hour past noon UTC. Seconds and nanos move it too,
    // so the terminator sweeps smoothly instead of stepping once a minute.
    val utcHours = utc.hour + utc.minute / 60.0 + utc.second / 3600.0 + utc.nano / 3_600_000_000_000.0
    return SubsolarPoint(latitude = declination, longitude = normalizeLongitude((12.0 - utcHours) * 15.0))
}

/**
 * The latitudes of one meridian that are in the dark, as low..high degrees, or null when the whole meridian is in
 * daylight.
 *
 * The sun's elevation factor at latitude `lat` is `sin(lat)sin(decl) + cos(lat)cos(decl)cos(hour)`, which is a
 * single sine wave in `lat`; its zeros are `lat = -phase + k * 180`, so at most two of them land inside the
 * 180 degrees a meridian spans and the dark half is one contiguous band. The band is always attached to a pole
 * or spans the whole meridian, so one rectangle per meridian is enough to draw it exactly.
 */
fun nightBand(longitude: Double, sun: SubsolarPoint): ClosedFloatingPointRange<Double>? {
    val hourAngle = Math.toRadians(longitude - sun.longitude)
    val declination = Math.toRadians(sun.latitude)

    fun elevation(latitudeDegrees: Double): Double {
        val latitude = Math.toRadians(latitudeDegrees)
        return sin(latitude) * sin(declination) + cos(latitude) * cos(declination) * cos(hourAngle)
    }

    val phase = atan2(cos(declination) * cos(hourAngle), sin(declination))
    val cuts =
        (-2..2)
            .mapNotNull { step ->
                val boundary = Math.toDegrees(-phase + step * PI)
                if (boundary > -90.0 && boundary < 90.0) boundary else null
            }.sorted()
    val edges = listOf(-90.0) + cuts + listOf(90.0)
    for (index in 0 until edges.lastIndex) {
        val low = edges[index]
        val high = edges[index + 1]
        if (high - low < 1e-6) continue
        if (elevation((low + high) / 2.0) < 0.0) return low..high
    }
    return null
}

/** Maps any angle into [-180, 180): 180 degrees west and 180 degrees east are the same meridian. */
private fun normalizeLongitude(degrees: Double): Double = ((degrees + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
