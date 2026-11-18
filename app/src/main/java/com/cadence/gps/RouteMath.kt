package com.cadence.gps

import kotlin.math.cos
import kotlin.math.roundToInt

/** A point in canvas pixels. */
data class PlotPoint(val x: Float, val y: Float)

/** Sum of great-circle distances between consecutive points, in metres. */
fun routeDistanceMeters(points: List<GpsFix>): Double {
    var total = 0.0
    for (i in 1 until points.size) {
        total += GpsDistance.haversineMeters(points[i - 1], points[i])
    }
    return total
}

/**
 * Maps route points into a [width] x [height] box, keeping the route's aspect ratio and
 * centring it. Longitude is scaled by cos(mean latitude) so east-west distances look right.
 * [paddingPx] is kept free on every side.
 *
 * Empty input gives an empty list. A single point, or points that all sit on one spot, are
 * placed at the centre. A straight line fills its long axis.
 */
fun fitRoute(
    points: List<GpsFix>,
    width: Float,
    height: Float,
    paddingPx: Float = 0f,
): List<PlotPoint> {
    if (points.isEmpty()) return emptyList()
    val centre = PlotPoint(width / 2f, height / 2f)
    if (points.size == 1) return listOf(centre)

    val meanLat = points.sumOf { it.latitude } / points.size
    val kx = cos(Math.toRadians(meanLat))
    // Canvas y grows downwards, so north (larger latitude) is drawn higher up.
    val xs = points.map { it.longitude * kx }
    val ys = points.map { -it.latitude }
    val minX = xs.min()
    val minY = ys.min()
    val spanX = xs.max() - minX
    val spanY = ys.max() - minY
    if (spanX == 0.0 && spanY == 0.0) return points.map { centre }

    val availW = (width - 2 * paddingPx).coerceAtLeast(0f).toDouble()
    val availH = (height - 2 * paddingPx).coerceAtLeast(0f).toDouble()
    val scale = when {
        spanX == 0.0 -> availH / spanY
        spanY == 0.0 -> availW / spanX
        else -> minOf(availW / spanX, availH / spanY)
    }
    val offsetX = paddingPx + (availW - spanX * scale) / 2.0
    val offsetY = paddingPx + (availH - spanY * scale) / 2.0

    return points.indices.map { i ->
        PlotPoint(
            x = (offsetX + (xs[i] - minX) * scale).toFloat(),
            y = (offsetY + (ys[i] - minY) * scale).toFloat(),
        )
    }
}

/** "850 m" below one kilometre, otherwise "1.24 km". */
fun formatRouteDistance(meters: Double): String =
    if (meters < 1000.0) {
        "${meters.roundToInt()} m"
    } else {
        "%.2f km".format(java.util.Locale.US, meters / 1000.0)
    }

/** Plain-language summary used as the TalkBack description of the route card. */
fun routeDescription(pointCount: Int, distanceMeters: Double): String =
    if (pointCount < 2) {
        "Today's route: no route yet"
    } else {
        "Today's route: ${formatRouteDistance(distanceMeters)}, $pointCount GPS points"
    }
