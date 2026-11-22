package com.cadence.patterns

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * One step of the walk drawing, in metres from where the drawing started. x grows to the east
 * (or to the right of the starting direction), y to the north. [pen] false means the step moved
 * the pen without drawing. [drawing] numbers the drawings of a day; a new drawing starts at 0, 0.
 */
data class TrackPoint(
    val timeMillis: Long,
    val x: Double,
    val y: Double,
    val pen: Boolean,
    val drawing: Int,
)

/**
 * Walking direction from a rotation matrix (row-major 3 by 3, device to world, as from
 * SensorManager.getRotationMatrixFromVector), in radians clockwise from the world's y axis.
 *
 * The forward direction is the device's top edge (+y) minus its screen normal (+z), projected on
 * the ground. Flat in the hand it is the top edge; held upright it is the direction the back of
 * the phone faces; tilted it is a mix of both, which point the same way. Returns null when the
 * phone is held so that no forward direction can be told (the projection is too short).
 */
fun forwardHeading(r: FloatArray): Double? {
    if (r.size < 9) return null
    val east = (r[1] - r[2]).toDouble()
    val north = (r[4] - r[5]).toDouble()
    if (hypot(east, north) < MIN_FORWARD_LENGTH) return null
    return atan2(east, north)
}

private const val MIN_FORWARD_LENGTH = 0.3

/** Stride for the walk drawing when nothing better is known, in metres. */
const val DEFAULT_DRAW_STRIDE_M = 0.7

/** Common estimate of walking stride from height. */
private const val STRIDE_PER_HEIGHT = 0.415

/**
 * Stride for the walk drawing, in metres: the user's override, else the GPS-learned stride,
 * else an estimate from height, else [DEFAULT_DRAW_STRIDE_M]. Values outside 0.2 to 2 m are ignored.
 */
fun drawStrideMeters(overrideCm: Double?, learnedCm: Double?, heightCm: Double?): Double {
    fun usable(m: Double?): Double? = m?.takeIf { it.isFinite() && it in 0.2..2.0 }
    return usable(overrideCm?.div(100.0))
        ?: usable(learnedCm?.div(100.0))
        ?: usable(heightCm?.times(STRIDE_PER_HEIGHT)?.div(100.0))
        ?: DEFAULT_DRAW_STRIDE_M
}

/** The point one stride on from [from] in direction [headingRad]. */
fun stepFrom(from: TrackPoint, headingRad: Double, strideM: Double, timeMillis: Long, pen: Boolean): TrackPoint =
    TrackPoint(
        timeMillis = timeMillis,
        x = from.x + strideM * sin(headingRad),
        y = from.y + strideM * cos(headingRad),
        pen = pen,
        drawing = from.drawing,
    )

/** The points of the newest drawing in [points], in order. */
fun latestDrawing(points: List<TrackPoint>): List<TrackPoint> {
    val last = points.maxOfOrNull { it.drawing } ?: return emptyList()
    return points.filter { it.drawing == last }
}

/** A screen position from [fitTrack]. */
data class ScreenPoint(val x: Float, val y: Float)

/** Smallest area shown, in metres, so the first steps do not fill the whole canvas. */
const val MIN_TRACK_SPAN_M = 4.0

/**
 * Scales [points] to fit [width] by [height] with [padding] free on every side, keeping the
 * shape (one scale for both axes) and centring it. North is up. The scale follows the drawing:
 * as the walk grows, everything shrinks to fit. Areas smaller than [MIN_TRACK_SPAN_M] are shown
 * at that size.
 */
fun fitTrack(points: List<TrackPoint>, width: Float, height: Float, padding: Float): List<ScreenPoint> {
    if (points.isEmpty()) return emptyList()
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val spanX = max(maxX - minX, MIN_TRACK_SPAN_M)
    val spanY = max(maxY - minY, MIN_TRACK_SPAN_M)
    val usableW = max(width - 2 * padding, 1f)
    val usableH = max(height - 2 * padding, 1f)
    val scale = min(usableW / spanX, usableH / spanY)
    val centreX = (minX + maxX) / 2
    val centreY = (minY + maxY) / 2
    return points.map {
        ScreenPoint(
            x = (width / 2 + (it.x - centreX) * scale).toFloat(),
            y = (height / 2 - (it.y - centreY) * scale).toFloat(),
        )
    }
}

/** Text form of track points, one per line: "time,x,y,pen,drawing". */
object TrackCodec {
    fun encode(point: TrackPoint): String =
        "${point.timeMillis},${"%.3f".format(java.util.Locale.ROOT, point.x)}," +
            "${"%.3f".format(java.util.Locale.ROOT, point.y)},${if (point.pen) 1 else 0},${point.drawing}"

    /** Parses one line. Returns null for a blank or damaged line, so one bad line loses only itself. */
    fun decode(line: String): TrackPoint? {
        val parts = line.trim().split(',')
        if (parts.size != 5) return null
        val time = parts[0].toLongOrNull() ?: return null
        val x = parts[1].toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        val y = parts[2].toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        val pen = when (parts[3]) {
            "1" -> true
            "0" -> false
            else -> return null
        }
        val drawing = parts[4].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return TrackPoint(time, x, y, pen, drawing)
    }

    fun decodeAll(text: String): List<TrackPoint> = text.lineSequence().mapNotNull { decode(it) }.toList()
}

/** Points averaged on each side when smoothing a drawing for display (STEPW-64). */
const val DISPLAY_SMOOTH_RADIUS = 2

/**
 * A copy of [points] smoothed for display only: each pen-down point becomes the average of its
 * neighbours within [radius] in the same pen-down run. The compass wobbles a little on every step,
 * which drew as zigzags. The ends of each run stay put, so runs still join. Saved points are not
 * changed.
 */
fun smoothForDisplay(points: List<TrackPoint>, radius: Int = DISPLAY_SMOOTH_RADIUS): List<TrackPoint> {
    if (radius <= 0 || points.size < 3) return points
    val out = ArrayList<TrackPoint>(points.size)
    var runStart = 0
    while (runStart < points.size) {
        // A run: consecutive points drawn with the pen down (the first point is where it starts).
        var runEnd = runStart
        while (runEnd + 1 < points.size && points[runEnd + 1].pen) runEnd++
        for (i in runStart..runEnd) {
            if (i == runStart || i == runEnd) {
                out.add(points[i])
                continue
            }
            val from = maxOf(runStart, i - radius)
            val to = minOf(runEnd, i + radius)
            var sx = 0.0
            var sy = 0.0
            for (j in from..to) {
                sx += points[j].x
                sy += points[j].y
            }
            val n = (to - from + 1).toDouble()
            out.add(points[i].copy(x = sx / n, y = sy / n))
        }
        runStart = runEnd + 1
    }
    return out
}

/** A stretch of the drawing to stroke: [pen] true draws a line, false a pen-up move. */
data class DrawSegment(val points: List<ScreenPoint>, val pen: Boolean)

/**
 * The drawing ready to stroke at [width] by [height]: smoothed, fitted (see [fitTrack]) and split
 * into pen-down and pen-up segments. Points closer than [minStepPx] to the last kept point are
 * dropped, so a long walk draws a few hundred segments instead of thousands. Segment ends are kept.
 */
fun drawingSegments(
    points: List<TrackPoint>,
    width: Float,
    height: Float,
    padding: Float,
    minStepPx: Float = 1.5f,
): List<DrawSegment> {
    if (points.size < 2) return emptyList()
    val screen = fitTrack(smoothForDisplay(points), width, height, padding)
    val segments = ArrayList<DrawSegment>()
    var i = 1
    while (i < points.size) {
        val pen = points[i].pen
        val kept = ArrayList<ScreenPoint>()
        kept.add(screen[i - 1])
        while (i < points.size && points[i].pen == pen) {
            val p = screen[i]
            val last = kept.last()
            val isEnd = i + 1 >= points.size || points[i + 1].pen != pen
            if (isEnd || hypot((p.x - last.x).toDouble(), (p.y - last.y).toDouble()) >= minStepPx) kept.add(p)
            i++
        }
        segments.add(DrawSegment(kept, pen))
    }
    return segments
}

/** Width and height of the drawing in metres (east-west, north-south). */
fun drawingSizeMeters(points: List<TrackPoint>): Pair<Double, Double> {
    if (points.isEmpty()) return 0.0 to 0.0
    return (points.maxOf { it.x } - points.minOf { it.x }) to (points.maxOf { it.y } - points.minOf { it.y })
}
