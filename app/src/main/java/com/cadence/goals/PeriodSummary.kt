package com.cadence.goals

import com.cadence.domain.DailyRecord
import java.time.LocalDate

/**
 * Steps and goal progress over one period (a week or a month so far).
 *
 * - [percent] is the true percent of the goal, rounded down, and can exceed 100. Null when there
 *   is no positive goal.
 * - [barFraction] is the percent capped to 0..1 for drawing a bar.
 * - [averagePerDay] is [steps] divided by [days], as a Double.
 */
data class PeriodSummary(
    val steps: Int,
    val goal: Int?,
    val days: Int,
    val percent: Int?,
    val barFraction: Float,
    val averagePerDay: Double,
)

/** Summarises [steps] taken over [days] days against an optional [goal]. */
fun periodSummary(steps: Int, goal: Int?, days: Int): PeriodSummary {
    require(steps >= 0) { "steps must not be negative, was $steps" }
    require(days > 0) { "days must be greater than 0, was $days" }
    val validGoal = goal?.takeIf { it > 0 }
    val percent = validGoal?.let { (steps.toLong() * 100L / it).toInt() }
    val barFraction = percent?.let { (it / 100f).coerceIn(0f, 1f) } ?: 0f
    return PeriodSummary(
        steps = steps,
        goal = validGoal,
        days = days,
        percent = percent,
        barFraction = barFraction,
        averagePerDay = steps.toDouble() / days,
    )
}

/** Total steps for the [days] days ending on [endDate], inclusive. Days with no row count as zero. */
fun stepsInLastDays(history: List<DailyRecord>, endDate: LocalDate, days: Int): Int {
    require(days > 0) { "days must be greater than 0, was $days" }
    return stepsBetween(history, endDate.minusDays((days - 1).toLong()), endDate)
}

/** Total steps from the first of [endDate]'s month up to and including [endDate]. */
fun stepsMonthToDate(history: List<DailyRecord>, endDate: LocalDate): Int =
    stepsBetween(history, endDate.withDayOfMonth(1), endDate)

/** Days elapsed in [endDate]'s month, counting [endDate]. Used as the divisor for month averages. */
fun daysMonthToDate(endDate: LocalDate): Int = endDate.dayOfMonth

private fun stepsBetween(history: List<DailyRecord>, from: LocalDate, to: LocalDate): Int {
    var total = 0L
    for (record in history) {
        val date = LocalDate.parse(record.dateIso)
        if (!date.isBefore(from) && !date.isAfter(to)) total += record.steps
    }
    return total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
