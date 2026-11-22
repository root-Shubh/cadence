package com.cadence.maps

import com.cadence.patterns.TrackPoint
import kotlin.math.abs
import kotlin.math.cos

/** Metres per degree of latitude (and of longitude at the equator). */
const val METERS_PER_DEGREE: Double = 111_320.0

/** A GPS fix further than this in time from every drawing step cannot place the drawing. */
const val MAX_ANCHOR_GAP_MS: Long = 15 * 60 * 1000L

/** A position on the map, with the time it was recorded (0 when unknown). */
data class GeoPoint(val lat: Double, val lon: Double, val timeMillis: Long = 0L)

/** Where the drawing's origin (0, 0) sits on the map, and how far in time the fix used was. */
data class DrawingAnchor(val originLat: Double, val originLon: Double, val gapMillis: Long)

/** The point [eastM] metres east and [northM] metres north of [lat], [lon]. Fine for walks. */
fun offsetLatLon(lat: Double, lon: Double, eastM: Double, northM: Double): Pair<Double, Double> {
    val newLat = lat + northM / METERS_PER_DEGREE
    val newLon = lon + eastM / (METERS_PER_DEGREE * cos(Math.toRadians(lat)))
    return newLat to newLon
}

/**
 * Places a drawing on the map. The drawing is in metres from where it started (x east, y north),
 * built from the compass and stride with no GPS. A GPS fix taken during the walk says where the
 * drawing was at that moment: the drawing point closest in time to a fix is pinned to it. The fix
 * with the smallest time gap is used. Returns null with no fix within [maxGapMs] of the drawing.
 */
fun anchorDrawing(
    drawing: List<TrackPoint>,
    fixes: List<GeoPoint>,
    maxGapMs: Long = MAX_ANCHOR_GAP_MS,
): DrawingAnchor? {
    if (drawing.isEmpty() || fixes.isEmpty()) return null
    val byTime = drawing.sortedBy { it.timeMillis }
    var best: Triple<GeoPoint, TrackPoint, Long>? = null
    for (fix in fixes) {
        val step = nearestInTime(byTime, fix.timeMillis)
        val gap = abs(step.timeMillis - fix.timeMillis)
        if (best == null || gap < best.third) best = Triple(fix, step, gap)
    }
    val (fix, step, gap) = best ?: return null
    if (gap > maxGapMs) return null
    // The fix is where the step was; the origin is that far back.
    val (lat, lon) = offsetLatLon(fix.lat, fix.lon, -step.x, -step.y)
    return DrawingAnchor(lat, lon, gap)
}

private fun nearestInTime(sorted: List<TrackPoint>, time: Long): TrackPoint {
    var lo = 0
    var hi = sorted.size - 1
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (sorted[mid].timeMillis < time) lo = mid + 1 else hi = mid
    }
    val after = sorted[lo]
    val before = sorted.getOrNull(lo - 1) ?: return after
    return if (abs(before.timeMillis - time) <= abs(after.timeMillis - time)) before else after
}

/** The drawing's points on the map for [anchor], in order. */
fun drawingOnMap(drawing: List<TrackPoint>, anchor: DrawingAnchor): List<GeoPoint> = drawing.map {
    val (lat, lon) = offsetLatLon(anchor.originLat, anchor.originLon, it.x, it.y)
    GeoPoint(lat, lon, it.timeMillis)
}

/** South-west and north-east corners around [points], or null when there are none. */
fun boundsOf(points: List<GeoPoint>): Pair<GeoPoint, GeoPoint>? {
    if (points.isEmpty()) return null
    return GeoPoint(points.minOf { it.lat }, points.minOf { it.lon }) to
        GeoPoint(points.maxOf { it.lat }, points.maxOf { it.lon })
}
