package com.cadence.goals

import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * User-set step goals. Each period is either unset (null) or a positive number of steps.
 */
data class Goals(
    val dailySteps: Int? = null,
    val weeklySteps: Int? = null,
    val monthlySteps: Int? = null,
) {
    init {
        dailySteps?.let { require(it > 0) { "dailySteps must be greater than 0, was $it" } }
        weeklySteps?.let { require(it > 0) { "weeklySteps must be greater than 0, was $it" } }
        monthlySteps?.let { require(it > 0) { "monthlySteps must be greater than 0, was $it" } }
    }
}

/** One day's step count. [dateIso] is an ISO-8601 local date such as 2026-03-14. */
data class StepPoint(val dateIso: String, val steps: Int) {
    init {
        require(steps >= 0) { "steps must not be negative, was $steps" }
        parseDate(dateIso)
    }
}

/** Progress for a single day. */
data class DailyProgress(
    val date: LocalDate,
    val steps: Int,
    val goal: Int,
    val deficit: Int,
    val met: Boolean,
)

/**
 * Progress for a rolling 7-day window [windowStart, windowEnd] ending on the given date (inclusive).
 * Weeks are not Monday-based; the window always ends on the date being reported.
 * [daysWithData] counts distinct dates in the window that have a step row.
 * [daysLeft] is the number of window days after the end date, which is always 0 for a window
 * that ends on the reported date.
 */
data class WeeklyProgress(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val steps: Int,
    val goal: Int,
    val deficit: Int,
    val met: Boolean,
    val daysWithData: Int,
    val daysLeft: Int,
)

/**
 * Progress for the calendar month containing the reported date, counting steps up to and
 * including that date. [daysLeftInMonth] includes the reported date.
 */
data class MonthlyProgress(
    val month: YearMonth,
    val steps: Int,
    val goal: Int,
    val deficit: Int,
    val met: Boolean,
    val daysLeftInMonth: Int,
)

/** Plain-English guidance derived from progress. Use [text] for display. */
sealed class GoalSuggestion {
    abstract fun text(): String

    /** Days at the current average pace needed to reach the weekly goal. */
    data class WeeklyDaysNeeded(
        val daysNeeded: Int,
        val averagePerDay: Int,
        val goal: Int,
    ) : GoalSuggestion() {
        override fun text(): String {
            val verb = if (daysNeeded == 1) "reaches" else "reach"
            val days = if (daysNeeded == 1) "1 more day" else "$daysNeeded more days"
            return "At your average of ${formatCount(averagePerDay)} a day, $days $verb " +
                "this week's ${formatCount(goal)} goal."
        }
    }

    /** No steps yet in the window, so there is no average to project from. */
    data class WeeklyStart(val goal: Int) : GoalSuggestion() {
        override fun text(): String =
            "Steps are needed to start toward this week's ${formatCount(goal)} goal."
    }

    /** Steps per day needed for the rest of the month (remaining days include today). */
    data class MonthlyExtraPerDay(val extraPerDay: Int, val goal: Int) : GoalSuggestion() {
        override fun text(): String =
            "Walk ${formatCount(extraPerDay)} more ${stepWord(extraPerDay)} a day for the rest " +
                "of the month to reach ${formatCount(goal)}."
    }

    /** The month has no days left and the goal was not reached. */
    data class Missed(val shortfall: Int, val goal: Int) : GoalSuggestion() {
        override fun text(): String =
            "The month ended ${formatCount(shortfall)} ${stepWord(shortfall)} short of " +
                "${formatCount(goal)}."
    }

    /** Steps still needed today. */
    data class DailyRemaining(val deficit: Int) : GoalSuggestion() {
        override fun text(): String =
            "You are ${formatCount(deficit)} ${stepWord(deficit)} from today's goal."
    }

    /** The goal for this period has been reached. */
    object Met : GoalSuggestion() {
        override fun text(): String = "Goal met."
    }
}

/**
 * Progress for today's goal. Returns null when no daily goal is set.
 * Steps for [endDateIso] are summed; a missing row counts as 0.
 */
fun dailyProgress(points: List<StepPoint>, goals: Goals, endDateIso: String): DailyProgress? {
    val goal = goals.dailySteps ?: return null
    val date = parseDate(endDateIso)
    val steps = points.between(date, date).sumOf { it.second }
    return DailyProgress(
        date = date,
        steps = steps,
        goal = goal,
        deficit = maxOf(0, goal - steps),
        met = steps >= goal,
    )
}

/**
 * Progress for the rolling 7-day window [end-6, end]. Returns null when no weekly goal is set.
 */
fun weeklyProgress(points: List<StepPoint>, goals: Goals, endDateIso: String): WeeklyProgress? {
    val goal = goals.weeklySteps ?: return null
    val end = parseDate(endDateIso)
    val start = end.minusDays(6)
    val rows = points.between(start, end)
    val steps = rows.sumOf { it.second }
    return WeeklyProgress(
        windowStart = start,
        windowEnd = end,
        steps = steps,
        goal = goal,
        deficit = maxOf(0, goal - steps),
        met = steps >= goal,
        daysWithData = rows.map { it.first }.distinct().size,
        daysLeft = 0,
    )
}

/**
 * Progress for the calendar month containing [endDateIso], counting steps up to that date.
 * Returns null when no monthly goal is set.
 */
fun monthlyProgress(points: List<StepPoint>, goals: Goals, endDateIso: String): MonthlyProgress? {
    val goal = goals.monthlySteps ?: return null
    val date = parseDate(endDateIso)
    val month = YearMonth.from(date)
    val steps = points.between(month.atDay(1), date).sumOf { it.second }
    return MonthlyProgress(
        month = month,
        steps = steps,
        goal = goal,
        deficit = maxOf(0, goal - steps),
        met = steps >= goal,
        daysLeftInMonth = month.lengthOfMonth() - date.dayOfMonth + 1,
    )
}

/** Suggestion for today's goal. */
fun dailySuggestion(goal: Int, steps: Int): GoalSuggestion {
    require(goal > 0) { "goal must be greater than 0, was $goal" }
    require(steps >= 0) { "steps must not be negative, was $steps" }
    return if (steps >= goal) GoalSuggestion.Met else GoalSuggestion.DailyRemaining(goal - steps)
}

/**
 * Suggestion for the weekly goal. [windowTotal] is the steps in the window and [daysWithData]
 * is the number of days in the window that have data. The average is taken over those days only.
 */
fun weeklySuggestion(goal: Int, windowTotal: Int, daysWithData: Int): GoalSuggestion {
    require(goal > 0) { "goal must be greater than 0, was $goal" }
    require(windowTotal >= 0) { "windowTotal must not be negative, was $windowTotal" }
    require(daysWithData >= 0) { "daysWithData must not be negative, was $daysWithData" }
    if (windowTotal >= goal) return GoalSuggestion.Met
    if (windowTotal == 0 || daysWithData == 0) return GoalSuggestion.WeeklyStart(goal)
    val deficit = (goal - windowTotal).toLong()
    // deficit / (windowTotal / daysWithData), rounded up, in exact integer arithmetic.
    val daysNeeded = ceilDiv(deficit * daysWithData, windowTotal.toLong()).toInt()
    val average = Math.round(windowTotal.toDouble() / daysWithData).toInt()
    return GoalSuggestion.WeeklyDaysNeeded(daysNeeded, average, goal)
}

/**
 * Suggestion for the monthly goal. [remainingDays] includes today. When it is 0 the month has
 * ended and the goal was not reached, so the shortfall is reported instead.
 */
fun monthlySuggestion(goal: Int, monthTotal: Int, remainingDays: Int): GoalSuggestion {
    require(goal > 0) { "goal must be greater than 0, was $goal" }
    require(monthTotal >= 0) { "monthTotal must not be negative, was $monthTotal" }
    if (monthTotal >= goal) return GoalSuggestion.Met
    val deficit = goal - monthTotal
    if (remainingDays <= 0) return GoalSuggestion.Missed(deficit, goal)
    return GoalSuggestion.MonthlyExtraPerDay(
        extraPerDay = ceilDiv(deficit.toLong(), remainingDays.toLong()).toInt(),
        goal = goal,
    )
}

private fun parseDate(iso: String): LocalDate =
    try {
        LocalDate.parse(iso)
    } catch (e: DateTimeException) {
        throw IllegalArgumentException("Not an ISO date: $iso", e)
    }

/** Rows whose date falls within [from, to] inclusive, as (date, steps) pairs. */
private fun List<StepPoint>.between(from: LocalDate, to: LocalDate): List<Pair<LocalDate, Int>> =
    map { parseDate(it.dateIso) to it.steps }
        .filter { (date, _) -> !date.isBefore(from) && !date.isAfter(to) }

private fun ceilDiv(numerator: Long, denominator: Long): Long =
    (numerator + denominator - 1) / denominator

private fun formatCount(value: Int): String = String.format(Locale.ROOT, "%,d", value)

private fun stepWord(value: Int): String = if (value == 1) "step" else "steps"
