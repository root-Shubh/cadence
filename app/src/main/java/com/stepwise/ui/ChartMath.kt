package com.stepwise.ui

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
