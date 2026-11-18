package com.stepwise.ui

import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Formats a measurement for the profile form: whole numbers without a decimal
 * point, otherwise rounded to one decimal place. Null becomes an empty string.
 */
fun formatMeasure(value: Double?): String {
    if (value == null) return ""
    val tenths = (value * 10.0).roundToLong() / 10.0
    return if (tenths == floor(tenths)) {
        tenths.toLong().toString()
    } else {
        tenths.toString()
    }
}
