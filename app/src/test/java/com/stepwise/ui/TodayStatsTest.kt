package com.stepwise.ui

import com.stepwise.domain.DailyRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TodayStatsTest {

    private val end = LocalDate.of(2026, 3, 14)

    private fun day(offset: Long, steps: Int) =
        DailyRecord(end.minusDays(offset).toString(), steps, 0.0)

    @Test
    fun goalPercentRoundsDownAndHandlesMissingGoal() {
        assertEquals(50, goalPercent(5000, 10000))
        assertEquals(33, goalPercent(3333, 10000))
        assertEquals(120, goalPercent(12000, 10000))
        assertEquals(0, goalPercent(0, 8000))
        assertNull(goalPercent(5000, null))
        assertNull(goalPercent(5000, 0))
    }

    @Test
    fun sevenDayAverageUsesLastSevenDaysWithData() {
        val history = listOf(day(0, 7000), day(1, 8000), day(2, 9000), day(9, 100000))
        // Three days with data inside the window: (7000 + 8000 + 9000) / 3 = 8000.
        assertEquals(8000, sevenDayAverage(history, end))
    }

    @Test
    fun sevenDayAverageIsNullWithoutData() {
        assertNull(sevenDayAverage(emptyList(), end))
        assertNull(sevenDayAverage(listOf(day(20, 5000)), end))
    }

    @Test
    fun streakCountsDaysAtOrAboveGoal() {
        val history = listOf(day(0, 10000), day(1, 12000), day(2, 9000), day(3, 11000))
        assertEquals(2, streakDays(history, end, 10000))
    }

    @Test
    fun streakIgnoresTodayWhileItIsStillBelowGoal() {
        val history = listOf(day(0, 1000), day(1, 12000), day(2, 11000))
        assertEquals(2, streakDays(history, end, 10000))
    }

    @Test
    fun streakCountsActiveDaysWithoutGoal() {
        val history = listOf(day(0, 500), day(1, 1), day(2, 0), day(3, 300))
        assertEquals(2, streakDays(history, end, null))
    }

    @Test
    fun streakIsZeroWithNoHistory() {
        assertEquals(0, streakDays(emptyList(), end, 8000))
    }
}
