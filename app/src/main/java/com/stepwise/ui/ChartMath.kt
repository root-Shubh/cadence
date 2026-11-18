package com.stepwise.ui

import androidx.compose.ui.geometry.Offset

/**
 * Max-normalises [values] into bar heights in 0..1.
 * Negative values are treated as zero. When every value is zero (or the list is empty),
 * all fractions are 0f. The largest value maps to exactly 1f.
 */
fun barFractions(values: List<Int>): List<Float> {
    if (values.isEmpty()) return emptyList()
    val max = values.maxOf { it.coerceAtLeast(0) }
    if (max == 0) return values.map { 0f }
    return values.map { it.coerceAtLeast(0).toFloat() / max.toFloat() }
}

private val SHORT_WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * Short English weekday name for [dateIso] (Mon..Sun), or "Today" when the date equals
 * [todayIso]. Uses fixed names so the output does not depend on the device locale.
 */
fun weekdayLabel(dateIso: String, todayIso: String): String {
    if (dateIso == todayIso) return "Today"
    val dayOfWeek = java.time.LocalDate.parse(dateIso).dayOfWeek.value
    return SHORT_WEEKDAYS[dayOfWeek - 1]
}

/**
 * Indices for axis labels over [count] items: every [step]-th index starting at 0, plus the
 * last index when it is not already included. Empty when [count] is 0.
 */
fun axisLabelIndices(count: Int, step: Int): List<Int> {
    require(step > 0) { "step must be > 0, was $step" }
    if (count <= 0) return emptyList()
    val indices = (0 until count step step).toMutableList()
    if (indices.last() != count - 1) indices.add(count - 1)
    return indices
}

private val SHORT_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "12 Mar" for axis labels. Fixed English names, independent of the device locale. */
fun dayMonthLabel(dateIso: String): String {
    val date = java.time.LocalDate.parse(dateIso)
    return "${date.dayOfMonth} ${SHORT_MONTHS[date.monthValue - 1]}"
}

/** "Mon 12 Mar" for tooltips. */
fun dayLabelLong(dateIso: String): String {
    val date = java.time.LocalDate.parse(dateIso)
    return "${SHORT_WEEKDAYS[date.dayOfWeek.value - 1]} ${dayMonthLabel(dateIso)}"
}

/**
 * Average steps per weekday over the points that have data, Mon..Sun (index 0..6).
 * A weekday with no data gives null.
 */
fun weekdayAverages(points: List<com.stepwise.domain.DayPoint>): List<Double?> {
    val sums = DoubleArray(7)
    val counts = IntArray(7)
    points.forEach { point ->
        if (!point.missing) {
            val index = java.time.LocalDate.parse(point.dateIso).dayOfWeek.value - 1
            sums[index] += point.steps.toDouble()
            counts[index]++
        }
    }
    return (0 until 7).map { if (counts[it] == 0) null else sums[it] / counts[it] }
}

/** Lowest tint alpha for the weekday heatmap. */
const val HEATMAP_MIN_ALPHA = 0.15f

/** Highest tint alpha for the weekday heatmap. */
const val HEATMAP_MAX_ALPHA = 1f

/**
 * Tint alpha for a heatmap cell: scales linearly from [HEATMAP_MIN_ALPHA] (zero) to
 * [HEATMAP_MAX_ALPHA] (the largest average). No data, or no positive maximum, gives the minimum.
 */
fun heatmapAlpha(average: Double?, maxAverage: Double?): Float {
    if (average == null || maxAverage == null || maxAverage <= 0.0) return HEATMAP_MIN_ALPHA
    val ratio = (average / maxAverage).coerceIn(0.0, 1.0)
    return (HEATMAP_MIN_ALPHA + (HEATMAP_MAX_ALPHA - HEATMAP_MIN_ALPHA) * ratio).toFloat()
}

/**
 * Trailing moving average over [window] days, ignoring missing days. Null where a window
 * holds no data. The output has the same length as [points].
 */
fun movingAverage(points: List<com.stepwise.domain.DayPoint>, window: Int): List<Double?> {
    require(window > 0) { "window must be > 0, was $window" }
    return points.indices.map { i ->
        val from = (i - window + 1).coerceAtLeast(0)
        val values = points.subList(from, i + 1).filter { !it.missing }.map { it.steps.toDouble() }
        if (values.isEmpty()) null else values.average()
    }
}

/**
 * Prefix of a polyline covering [fraction] (0..1) of its total length, ending on an interpolated
 * point. Used for the draw-on animation. Empty when the fraction is 0 or there are fewer than two points.
 */
fun trimPolyline(points: List<Offset>, fraction: Float): List<Offset> {
    if (points.size < 2) return emptyList()
    val f = fraction.coerceIn(0f, 1f)
    if (f <= 0f) return emptyList()
    if (f >= 1f) return points
    val total = (0 until points.size - 1).sumOf { segmentLength(points[it], points[it + 1]).toDouble() }.toFloat()
    var remaining = total * f
    val out = mutableListOf(points[0])
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        val length = segmentLength(a, b)
        if (length >= remaining) {
            val t = if (length > 0f) remaining / length else 0f
            out += Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            return out
        }
        remaining -= length
        out += b
    }
    return out
}

private fun segmentLength(a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

/**
 * Index of the chart column under horizontal position [x] for [count] columns across [width],
 * or null when there is nothing to pick.
 */
fun tapIndex(x: Float, width: Float, count: Int): Int? {
    if (count <= 0 || width <= 0f) return null
    val slot = width / count
    return (x / slot).toInt().coerceIn(0, count - 1)
}

/** Compact step count for small cells: full separators below 10,000, then "12.3k". */
fun compactSteps(value: Double): String =
    if (value < 10_000.0) formatCount(value.toLong())
    else String.format(java.util.Locale.ROOT, "%.1fk", value / 1000.0)
