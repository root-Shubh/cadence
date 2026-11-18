package com.cadence.ui

import com.cadence.domain.DailyRecord
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Length of the Week progress window, counting [endDate] as its last day. */
const val WEEK_WINDOW_DAYS = 7

/**
 * Share of [goal] reached by [steps], clamped to 0..1. Returns 0 when [goal] is not positive.
 */
fun progressFraction(steps: Int, goal: Int): Float {
    if (goal <= 0) return 0f
    return (steps.toDouble() / goal.toDouble()).toFloat().coerceIn(0f, 1f)
}

/** Label such as "1,250 of 10,000 steps", with thousands separators. */
fun progressLabel(steps: Int, goal: Int): String =
    String.format(Locale.ROOT, "%,d of %,d steps", steps, goal)

/** Sum of steps for the [days] days ending on [endDate] (inclusive). Missing days count as zero. */
fun windowStepTotal(history: List<DailyRecord>, endDate: LocalDate, days: Int): Int {
    val start = endDate.minusDays((days - 1).toLong())
    return history.sumOf { record ->
        val date = parseIsoOrNull(record.dateIso)
        if (date != null && !date.isBefore(start) && !date.isAfter(endDate)) record.steps else 0
    }
}

/** Sum of steps for the calendar month of [endDate], up to and including [endDate]. */
fun monthStepTotal(history: List<DailyRecord>, endDate: LocalDate): Int {
    val month = YearMonth.from(endDate)
    return history.sumOf { record ->
        val date = parseIsoOrNull(record.dateIso)
        if (date != null && YearMonth.from(date) == month && !date.isAfter(endDate)) record.steps else 0
    }
}

private fun parseIsoOrNull(iso: String): LocalDate? = try {
    LocalDate.parse(iso)
} catch (e: java.time.format.DateTimeParseException) {
    null
}
