package com.stepwise.goals

/**
 * Fills in weekly and monthly goals from the daily goal when they are not set explicitly.
 *
 * - If [Goals.dailySteps] is set and [Goals.weeklySteps] is null, weekly = dailySteps * 7.
 * - If [Goals.dailySteps] is set and [Goals.monthlySteps] is null, monthly = dailySteps * 30.
 *   This is a simple rule: every month is treated as 30 days, not the calendar length.
 * - Explicit weekly or monthly values always win over the derived ones.
 * - A null daily goal leaves weekly and monthly exactly as they were set.
 *
 * Derived values are capped at [Int.MAX_VALUE] rather than overflowing.
 */
fun effectiveGoals(goals: Goals): Goals {
    val daily = goals.dailySteps ?: return goals
    return goals.copy(
        weeklySteps = goals.weeklySteps ?: saturatedTimes(daily, 7),
        monthlySteps = goals.monthlySteps ?: saturatedTimes(daily, 30),
    )
}

private fun saturatedTimes(value: Int, factor: Int): Int =
    minOf(Int.MAX_VALUE.toLong(), value.toLong() * factor).toInt()
