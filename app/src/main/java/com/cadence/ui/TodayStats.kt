package com.cadence.ui

import com.cadence.domain.DailyRecord
import com.cadence.domain.computeTrend
import java.time.LocalDate
import kotlin.math.roundToInt

/** Percent of [dailyGoal] reached by [steps], rounded down. Null when there is no positive goal. */
fun goalPercent(steps: Int, dailyGoal: Int?): Int? {
    if (dailyGoal == null || dailyGoal <= 0) return null
    return (steps.toLong() * 100L / dailyGoal).toInt()
}

/** Average steps over the 7 days ending on [endDate], using the trend window. Null when no day has data. */
fun sevenDayAverage(history: List<DailyRecord>, endDate: LocalDate): Int? =
    computeTrend(history, endDate, 7).avgSteps?.roundToInt()

/**
 * Consecutive days at or above [dailyGoal], or days with any steps when there is no goal.
 * Counts back from [endDate]; if that day has not qualified yet, counting starts the day before.
 */
fun streakDays(history: List<DailyRecord>, endDate: LocalDate, dailyGoal: Int?): Int {
    val threshold = if (dailyGoal != null && dailyGoal > 0) dailyGoal else 1
    val byDate = history.associate { record ->
        val date = LocalDate.parse(record.dateIso)
        date to Day(date, record.steps, record.totalKcal)
    }
    return goalStreaks(byDate, endDate, threshold).current
}
