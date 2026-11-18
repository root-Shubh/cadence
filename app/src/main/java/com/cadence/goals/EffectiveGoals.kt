package com.cadence.goals

/**
 * Fills in the goals that are not set, so setting any one of daily, weekly or monthly
 * sets the others.
 *
 * - The base daily goal is the daily goal if set, otherwise weekly / 7, otherwise monthly / 30.
 * - Weekly (if not set) = base daily * 7.
 * - Monthly (if not set) = base daily * 30. Every month is treated as 30 days.
 * - Explicit values always win over derived ones.
 * - All null stays null.
 *
 * Derived values are capped at [Int.MAX_VALUE] rather than overflowing.
 */
fun effectiveGoals(goals: Goals): Goals {
    val daily = goals.dailySteps
        ?: goals.weeklySteps?.let { it / 7 }
        ?: goals.monthlySteps?.let { it / 30 }
        ?: return goals
    return goals.copy(
        dailySteps = daily,
        weeklySteps = goals.weeklySteps ?: saturatedTimes(daily, 7),
        monthlySteps = goals.monthlySteps ?: saturatedTimes(daily, 30),
    )
}

private fun saturatedTimes(value: Int, factor: Int): Int =
    minOf(Int.MAX_VALUE.toLong(), value.toLong() * factor).toInt()
