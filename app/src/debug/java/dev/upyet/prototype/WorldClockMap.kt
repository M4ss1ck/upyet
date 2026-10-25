// PROTOTYPE for #15, throwaway: the world map card that now sits above the DeskClock list. It draws the
// 1:110m land rings the prototype-data converter wrote, marks every listed place and the home place, and
// shades the night half of the planet - all from one Canvas, so it costs the list nothing but a marker list.
//
// The projection is equirectangular and cropped at both poles: lat 84..-66 is exactly 150 degrees against
// the 360 the longitudes take, which makes the card 2.4:1 and keeps Antarctica (whose -90 ring would eat a
// quarter of the height) out of frame. Nothing here is mercator-correct or navigation-grade; it is a
// "where are my places" map, and the marker is the answer, not the coastline.
package dev.upyet.prototype

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Cropped at both poles: 84 north is the northernmost land worth a pixel, -66 keeps Antarctica off. */
private const val MAP_LAT_TOP = 84f
private const val MAP_LAT_BOTTOM = -66f
private const val MAP_ASPECT = 360f / (MAP_LAT_TOP - MAP_LAT_BOTTOM)

/** One degree per column: a stair step narrower than a pixel at phone width, and no degenerate polygons. */
private const val NIGHT_COLUMNS = 360

/** How far from a marker a tap still counts as tapping it, in dp. Fingers are not pixels. */
private val markerTouchRadius = 20.dp

/**
 * A listed place on the map: its screen position, resolved once per card size, plus whether it is the home
 * place (one marker, drawn in the accent colour, never twice).
 */
private class MapMarker(val city: WorldCity, val center: Offset, val isHome: Boolean)

/**
 * The map card: land, then the night scrim, then every marker, then the labels that fit.
 *
 * [onMarkerTap] is the second half of the map/list link - tapping near a marker selects the row it belongs
 * to - and [selectedKey] is the first half, the row that is already selected. Both are the same piece of
 * state, so the two directions cannot disagree.
 */
@Composable
internal fun WorldMapCard(
    places: List<WorldCity>,
    home: WorldCity?,
    land: List<FloatArray>,
    selectedKey: String?,
    now: Instant,
    onMarkerTap: (WorldCity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ocean = MaterialTheme.colorScheme.surfaceVariant
    val landColor = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = ink)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // The card is measured before it can be drawn: every marker position is in pixels, so the first frame
    // (zero size) draws nothing and the second one draws the map.
    var size by remember { mutableStateOf(IntSize.Zero) }
    val landPath = remember(land, size) { buildLandPath(land, size.width.toFloat(), size.height.toFloat()) }
    val nightPath = remember(now, size) { buildNightPath(now, size.width.toFloat(), size.height.toFloat()) }
    // The list is read here, not only passed through: a SnapshotStateList keeps its identity when an item is
    // added, removed or moved, so keying the marker build on the reference alone would leave the map showing
    // the list it had a moment ago. The keys are a value the recomposition can compare.
    val placeKeys = places.map { it.key }
    val markers = remember(placeKeys, home, size) { markersOf(places, home, size.width.toFloat(), size.height.toFloat()) }
    val touchRadius = with(density) { markerTouchRadius.toPx() }

    Canvas(
        modifier =
        modifier
            .fillMaxWidth()
            .aspectRatio(MAP_ASPECT)
            .clip(RoundedCornerShape(18.dp))
            .background(ocean)
            .onSizeChanged { size = it }.pointerInput(markers, touchRadius) {
                detectTapGestures { tap ->
                    val hit =
                        markers
                            .map { it to (it.center - tap).getDistance() }
                            .filter { (_, distance) -> distance <= touchRadius }
                            .minByOrNull { (_, distance) -> distance }
                    hit?.let { (marker, _) -> onMarkerTap(marker.city) }
                }
            },
    ) {
        // One path for every landmass, even-odd so the interior rings the extract carries (the Caspian
        // shore) stay water instead of being filled solid.
        drawPath(landPath, landColor)
        nightPath?.let { drawPath(it, nightScrim) }
        markers.forEach { marker -> drawMarker(marker, marker.city.key == selectedKey, accent = accent, ink = ink) }
        drawLabels(markers, textMeasurer, labelStyle)
    }
}

/** Longitudes map across the full width, latitudes from [MAP_LAT_TOP] down to [MAP_LAT_BOTTOM]. */
private fun projectX(longitude: Float, width: Float): Float = (longitude + 180f) / 360f * width

private fun projectY(latitude: Float, height: Float): Float = (MAP_LAT_TOP - latitude) / (MAP_LAT_TOP - MAP_LAT_BOTTOM) * height

/**
 * The coastline as one path, built once per card size. Points outside the crop (Antarctica, the far north of
 * Greenland) need no clipping here: the card's clip modifier cuts them off, and clipping 5143 points by hand
 * would only move the same work into this function.
 */
private fun buildLandPath(rings: List<FloatArray>, width: Float, height: Float): Path {
    val path = Path()
    path.fillType = PathFillType.EvenOdd
    if (width <= 0f || height <= 0f) return path
    rings.forEach { ring ->
        if (ring.size < 6) return@forEach
        path.moveTo(projectX(ring[0], width), projectY(ring[1], height))
        var index = 2
        while (index < ring.size) {
            path.lineTo(projectX(ring[index], width), projectY(ring[index + 1], height))
            index += 2
        }
        path.close()
    }
    return path
}

/**
 * The night half of the planet as a translucent scrim, computed from the subsolar point rather than from a
 * timezone table: "is it dark there" is the same question as "is the sun below that horizon".
 *
 * Drawn as one-degree columns rather than as a traced terminator curve. The night set of a meridian is always
 * either empty, the whole meridian or a band attached to one pole, so a column is exactly one rectangle; the
 * rectangles share their edges to the bit, which is what keeps the seams invisible and the code free of the
 * degenerate cases a trapezoid build has to special-case.
 */
private fun buildNightPath(now: Instant, width: Float, height: Float): Path? {
    if (width <= 0f || height <= 0f) return null
    val utc = now.atZone(ZoneOffset.UTC)
    // Declination is within a fraction of a degree over the whole year; a 2-pixel terminator needs no more.
    val declination = Math.toRadians(-23.44 * cos(2.0 * PI * (utc.dayOfYear + 10) / 365.0))
    // The sun stands overhead at 15 degrees west for every hour past noon UTC.
    val subsolarLongitude = (12.0 - (utc.hour + utc.minute / 60.0)) * 15.0
    val path = Path()
    var columns = 0
    repeat(NIGHT_COLUMNS) { column ->
        val longitude = -180.0 + 360.0 * (column + 0.5) / NIGHT_COLUMNS
        val band = nightBand(hourAngle = Math.toRadians(longitude - subsolarLongitude), declination = declination) ?: return@repeat
        columns += 1
        val left = width * column / NIGHT_COLUMNS
        val right = width * (column + 1) / NIGHT_COLUMNS
        path.addRect(Rect(left = left, top = projectY(band.second, height), right = right, bottom = projectY(band.first, height)))
    }
    return if (columns == 0) null else path
}

/**
 * The latitudes of one meridian that are in the dark, as [lo, hi] in degrees, or null when the whole meridian
 * is in daylight.
 *
 * The sun's elevation factor at latitude `lat` is `sin(lat)sin(decl) + cos(lat)cos(decl)cos(hour)`, which is a
 * single sine wave in `lat`; its zeros are `lat = -phase + k * 180`, so at most two of them land inside the
 * 180 degrees a meridian spans and the dark half is one contiguous band.
 */
private fun nightBand(hourAngle: Double, declination: Double): Pair<Float, Float>? {
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
        if (elevation((low + high) / 2.0) < 0.0) return low.toFloat() to high.toFloat()
    }
    return null
}

/** A dot with a small ring; the home place is filled with the accent, a selected one grows and takes it too. */
private fun DrawScope.drawMarker(marker: MapMarker, selected: Boolean, accent: Color, ink: Color) {
    val fill = if (marker.isHome || selected) accent else ink
    val dot = when {
        selected -> 5.dp.toPx()
        marker.isHome -> 4.dp.toPx()
        else -> 3.dp.toPx()
    }
    drawCircle(color = fill, radius = dot, center = marker.center)
    drawCircle(
        color = fill.copy(alpha = if (selected) 1f else 0.55f),
        radius = dot + 3.dp.toPx(),
        center = marker.center,
        style = Stroke(width = 1.dp.toPx()),
    )
}

/**
 * The city name beside every marker that has room for it: one greedy pass, first come first served, and a
 * label whose rectangle meets an already placed one or leaves the card is dropped. Nothing is nudged into
 * place - a map whose labels wander is harder to read than a map with fewer labels.
 */
private fun DrawScope.drawLabels(markers: List<MapMarker>, textMeasurer: TextMeasurer, style: TextStyle) {
    val placed = ArrayList<Rect>(markers.size)
    val gap = 7.dp.toPx()
    markers.forEach { marker ->
        val layout = textMeasurer.measure(marker.city.displayName(), style = style, maxLines = 1)
        val left = marker.center.x + gap
        val top = marker.center.y - layout.size.height / 2f
        val bounds = Rect(left, top, left + layout.size.width, top + layout.size.height)
        if (bounds.left < 0f || bounds.top < 0f || bounds.right > size.width || bounds.bottom > size.height) return@forEach
        if (placed.any { it.overlaps(bounds) }) return@forEach
        placed.add(bounds)
        // The label is drawn in the same ink as the map's own lines: legible on land and on ocean, and it
        // never pretends to be a control.
        drawText(layout, topLeft = Offset(bounds.left, bounds.top))
    }
}

/** Every listed place, plus the home place when the device's zone has a city in the extract. */
private fun markersOf(places: List<WorldCity>, home: WorldCity?, width: Float, height: Float): List<MapMarker> {
    if (width <= 0f || height <= 0f) return emptyList()
    val markers = ArrayList<MapMarker>(places.size + 1)
    home?.let { markers.add(MapMarker(it, projection(it, width, height), isHome = true)) }
    places.forEach { place ->
        if (place.key == home?.key) return@forEach
        markers.add(MapMarker(place, projection(place, width, height), isHome = false))
    }
    return markers
}

private fun projection(city: WorldCity, width: Float, height: Float): Offset =
    Offset(projectX(city.longitude.toFloat(), width), projectY(city.latitude.toFloat(), height))

/** Fixed rather than themed: a dark scrim reads as night on a light map and on a dark one alike. */
private val nightScrim = Color(0xFF0B1226).copy(alpha = 0.32f)
