package dev.upyet.worldclock.ui

import android.icu.text.TimeZoneNames
import android.icu.util.ULocale
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

/** A zone's own localized label, ICU's exemplar city ("Los Angeles" for America/Los_Angeles), per ADR 0011. */
fun interface ZoneLabeler {
    fun label(zone: ZoneId, locale: Locale): String?
}

/**
 * Reads the label from ICU, which ships with the platform since API 24, so no bundled name table is needed.
 * Null when ICU has no exemplar city for the zone (the list shows the zone's offset alone then).
 */
class IcuZoneLabeler @Inject constructor() : ZoneLabeler {
    override fun label(zone: ZoneId, locale: Locale): String? = TimeZoneNames.getInstance(ULocale.forLocale(locale))
        .getExemplarLocationName(zone.id)
        ?.takeIf { it.isNotBlank() }
}
