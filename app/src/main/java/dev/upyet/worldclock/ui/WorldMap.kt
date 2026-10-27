package dev.upyet.worldclock.ui

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.upyet.R
import dev.upyet.worldclock.data.WorldLand
import dev.upyet.worldclock.domain.SubsolarPoint
import dev.upyet.worldclock.domain.nightBand
import dev.upyet.worldclock.domain.subsolarPoint
import java.time.Instant

/** Cropped at both poles: 84 north is the northernmost land worth a pixel, -66 keeps Antarctica off. */
private const val MAP_LAT_TOP = 84f
private const val MAP_LAT_BOTTOM = -66f
private const val MAP_ASPECT = 360f / (MAP_LAT_TOP - MAP_LAT_BOTTOM)

/** One degree per column: a stair step narrower than a pixel at phone width, and no degenerate polygons. */
private const val NIGHT_COLUMNS = 360

/** Land rings store hundredths of a degree. */
private const val HUNDREDTHS = 100f

/** How far from a marker a tap still counts as tapping it. Fingers are not pixels. */
private val MARKER_TOUCH_RADIUS = 20.dp

private val COASTLINE_WIDTH = 0.75.dp

/** Fixed rather than themed: a dark scrim reads as night on a light map and on a dark one alike. */
private val NIGHT_SCRIM = Color(0xFF0B1226).copy(alpha = 0.32f)

/** A place on the map. [id] is the GeoNames id of a listed place; the home marker of an unlisted place has none. */
internal data class MapPlace(val id: Long?, val name: String, val latitude: Double, val longitude: Double)

private class MapMarker(val place: MapPlace, val center: Offset, val isHome: Boolean)

/**
 * The map card (issue #15, equirectangular): land, then the night scrim, then every marker, then the labels that
 * fit. [onPlaceTap] is the map-to-list half of the link; [selectedId] is the list-to-map half. They are the same
 * piece of state, so the two directions cannot disagree.
 */
@Composable
internal fun WorldMap(
    land: WorldLand?,
    now: Instant,
    places: List<MapPlace>,
    home: MapPlace?,
    selectedId: Long?,
    onPlaceTap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val ink = colors.onSurfaceVariant
    // The theme's label tracking is set for chips and buttons; spread out on a map it reads as noise.
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = ink, letterSpacing = 0.sp)
    val textMeasurer = rememberTextMeasurer()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val landPath = remember(land, size) { buildLandPath(land, size.width.toFloat(), size.height.toFloat()) }
    val nightPath = remember(now, size) { buildNightPath(subsolarPoint(now), size.width.toFloat(), size.height.toFloat()) }
    val markers = remember(places, home, size) { markersOf(places, home, size.width.toFloat(), size.height.toFloat()) }
    val touchRadius = with(LocalDensity.current) { MARKER_TOUCH_RADIUS.toPx() }
    val description = pluralStringResource(R.plurals.world_clock_map_description, places.size, places.size)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(MAP_ASPECT)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceVariant)
            .onSizeChanged { size = it }
            .pointerInput(markers, touchRadius) {
                detectTapGestures { tap ->
                    markers
                        .filter { it.place.id != null && (it.center - tap).getDistance() <= touchRadius }
                        .minByOrNull { (it.center - tap).getDistance() }
                        ?.place
                        ?.id
                        ?.let(onPlaceTap)
                }
            }
            .semantics { contentDescription = description }
            .testTag("world_clock_map"),
    ) {
        drawPath(landPath, colors.surface)
        // surface on surfaceVariant is a faint step in the light scheme; the coastline keeps land legible there.
        drawPath(landPath, colors.outlineVariant, style = Stroke(width = COASTLINE_WIDTH.toPx()))
        nightPath?.let { drawPath(it, NIGHT_SCRIM) }
        markers.forEach { drawMarker(it, selected = it.place.id != null && it.place.id == selectedId, accent = colors.primary, ink = ink) }
        drawLabels(markers, textMeasurer, labelStyle)
    }
}

private fun projectX(longitude: Float, width: Float): Float = (longitude + 180f) / 360f * width

private fun projectY(latitude: Float, height: Float): Float = (MAP_LAT_TOP - latitude) / (MAP_LAT_TOP - MAP_LAT_BOTTOM) * height

/**
 * The coastline as one even-odd path, so interior rings (the Caspian shore) stay water. Points outside the crop
 * need no clipping: the card's clip modifier cuts them off.
 */
private fun buildLandPath(land: WorldLand?, width: Float, height: Float): Path {
    val path = Path()
    path.fillType = PathFillType.EvenOdd
    if (land == null || width <= 0f || height <= 0f) return path
    land.rings.forEach { ring ->
        if (ring.size < MIN_RING_VALUES) return@forEach
        path.moveTo(projectX(ring[0] / HUNDREDTHS, width), projectY(ring[1] / HUNDREDTHS, height))
        for (index in 2 until ring.size step 2) {
            path.lineTo(projectX(ring[index] / HUNDREDTHS, width), projectY(ring[index + 1] / HUNDREDTHS, height))
        }
        path.close()
    }
    return path
}

/** Three points make a polygon. */
private const val MIN_RING_VALUES = 6

/**
 * The night half as one-degree columns. [nightBand] says a meridian's dark part is empty, all of it or a band at
 * one pole, so each column is exactly one rectangle and neighbours share their edges to the bit.
 */
private fun buildNightPath(sun: SubsolarPoint, width: Float, height: Float): Path? {
    if (width <= 0f || height <= 0f) return null
    val path = Path()
    var columns = 0
    repeat(NIGHT_COLUMNS) { column ->
        val longitude = -180.0 + 360.0 * (column + 0.5) / NIGHT_COLUMNS
        val band = nightBand(longitude, sun) ?: return@repeat
        columns += 1
        path.addRect(
            Rect(
                left = width * column / NIGHT_COLUMNS,
                top = projectY(band.endInclusive.toFloat(), height),
                right = width * (column + 1) / NIGHT_COLUMNS,
                bottom = projectY(band.start.toFloat(), height),
            ),
        )
    }
    return if (columns == 0) null else path
}

/** A dot with a ring; the home marker and a selected one take the accent, a selected one grows. */
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
 * The name beside every marker that has room for it: one greedy pass, first come first served. A label goes to the
 * marker's right, or to its left when the right side leaves the card (Tokyo, Auckland); one that fits neither side
 * or meets an already placed label is dropped. Nothing else is nudged: a map whose labels wander is harder to read
 * than one with fewer labels.
 */
private fun DrawScope.drawLabels(markers: List<MapMarker>, textMeasurer: TextMeasurer, style: TextStyle) {
    val placed = ArrayList<Rect>(markers.size)
    // Clears the widest marker ring (selected: 5 dp dot + 3 dp ring) with room to spare.
    val gap = 11.dp.toPx()
    markers.forEach { marker ->
        val layout = textMeasurer.measure(marker.place.name, style = style, maxLines = 1)
        val width = layout.size.width.toFloat()
        val top = marker.center.y - layout.size.height / 2f
        val bounds = listOf(marker.center.x + gap, marker.center.x - gap - width)
            .map { left -> Rect(left, top, left + width, top + layout.size.height) }
            .firstOrNull { it.left >= 0f && it.top >= 0f && it.right <= size.width && it.bottom <= size.height }
            ?: return@forEach
        if (placed.any { it.overlaps(bounds) }) return@forEach
        placed.add(bounds)
        drawText(layout, topLeft = Offset(bounds.left, bounds.top))
    }
}

/** Every listed place, plus the home marker; a listed place that is the home place is drawn once, as home. */
private fun markersOf(places: List<MapPlace>, home: MapPlace?, width: Float, height: Float): List<MapMarker> {
    if (width <= 0f || height <= 0f) return emptyList()
    val markers = ArrayList<MapMarker>(places.size + 1)
    val homeListed = home?.id != null && places.any { it.id == home.id }
    if (home != null && !homeListed) markers.add(MapMarker(home, project(home, width, height), isHome = true))
    places.forEach { markers.add(MapMarker(it, project(it, width, height), isHome = homeListed && it.id == home.id)) }
    return markers
}

private fun project(place: MapPlace, width: Float, height: Float): Offset =
    Offset(projectX(place.longitude.toFloat(), width), projectY(place.latitude.toFloat(), height))
