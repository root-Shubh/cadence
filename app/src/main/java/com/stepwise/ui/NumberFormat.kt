package com.stepwise.ui

import java.util.Locale
import kotlin.math.roundToLong

/** Formats a count with "," thousands separators, independent of the device locale. */
fun formatCount(value: Long): String = String.format(Locale.ROOT, "%,d", value)

/** Formats kilocalories rounded to the nearest whole number with separators. */
fun formatKcal(value: Double): String = formatCount(value.roundToLong())

/** Formats a percentage to one decimal place with an explicit sign, e.g. "-7.4%" or "+3.0%". */
fun formatPercent(value: Double): String = String.format(Locale.ROOT, "%+.1f%%", value)
