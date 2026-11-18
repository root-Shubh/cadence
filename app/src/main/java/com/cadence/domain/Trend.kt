package com.cadence.domain

import java.time.LocalDate

/** One stored day of history. [dateIso] is ISO-8601 (yyyy-MM-dd). */
data class DailyRecord(val dateIso: String, val steps: Int, val totalKcal: Double)

/** One day in a trend window. Missing days carry zeros and [missing] = true. */
data class DayPoint(val dateIso: String, val steps: Int, val totalKcal: Double, val missing: Boolean)

/**
 * Summary of a trend window ending at [endDateIso].
 * [avgSteps] and [avgKcal] average only days that have data; null when none do.
 * [percentChangeSteps] compares [avgSteps] with the previous window of the same length;
 * null when the previous window has no data (or its average is zero).
 */
data class TrendSummary(
    val windowDays: Int,
    val endDateIso: String,
    val series: List<DayPoint>,
    val avgSteps: Double?,
    val avgKcal: Double?,
    val totalSteps: Long,
    val percentChangeSteps: Double?,
)

/**
 * Builds a trend for a window of [windowDays] days ending on [endDate] (inclusive).
 * Assumption: window length must be positive; the spec's 7 and 30 are the intended values.
 * If history contains several records for one date, the last one wins.
 * Empty history is valid and yields a fully missing series.
 */
fun computeTrend(
    history: List<DailyRecord>,
    endDate: LocalDate,
    windowDays: Int,
): TrendSummary {
    require(windowDays > 0) { "windowDays must be > 0, was $windowDays" }

    val byDate: Map<LocalDate, DailyRecord> = history
        .associateBy { LocalDate.parse(it.dateIso) }

    val currentStart = endDate.minusDays((windowDays - 1).toLong())
    val current = windowRecords(byDate, currentStart, endDate)
    val previousEnd = currentStart.minusDays(1)
    val previousStart = previousEnd.minusDays((windowDays - 1).toLong())
    val previous = windowRecords(byDate, previousStart, previousEnd)

    val series = (0 until windowDays).map { offset ->
        val date = currentStart.plusDays(offset.toLong())
        val rec = byDate[date]
        if (rec != null) DayPoint(date.toString(), rec.steps, rec.totalKcal, false)
        else DayPoint(date.toString(), 0, 0.0, true)
    }

    val avgSteps = current.map { it.steps.toDouble() }.average().takeIf { current.isNotEmpty() }
    val avgKcal = current.map { it.totalKcal }.average().takeIf { current.isNotEmpty() }
    val prevAvgSteps = previous.map { it.steps.toDouble() }.average().takeIf { previous.isNotEmpty() }

    val percentChange = if (avgSteps != null && prevAvgSteps != null && prevAvgSteps != 0.0) {
        (avgSteps - prevAvgSteps) / prevAvgSteps * 100.0
    } else {
        null
    }

    return TrendSummary(
        windowDays = windowDays,
        endDateIso = endDate.toString(),
        series = series,
        avgSteps = avgSteps,
        avgKcal = avgKcal,
        totalSteps = current.sumOf { it.steps.toLong() },
        percentChangeSteps = percentChange,
    )
}

private fun windowRecords(
    byDate: Map<LocalDate, DailyRecord>,
    start: LocalDate,
    end: LocalDate,
): List<DailyRecord> {
    val out = ArrayList<DailyRecord>()
    var d = start
    while (!d.isAfter(end)) {
        byDate[d]?.let { out.add(it) }
        d = d.plusDays(1)
    }
    return out
}
