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
