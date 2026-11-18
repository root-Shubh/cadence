package com.cadence.goals

import com.cadence.domain.DailyRecord
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodSummaryTest {

    @Test
    fun percentIsTruePercentAndBarIsCapped() {
        val over = periodSummary(steps = 14_000, goal = 7_000, days = 7)
        assertEquals(200, over.percent)
        assertEquals(1f, over.barFraction, 0f)

        val half = periodSummary(steps = 3_500, goal = 7_000, days = 7)
        assertEquals(50, half.percent)
        assertEquals(0.5f, half.barFraction, 0.0001f)
    }

    @Test
    fun percentRoundsDown() {
        assertEquals(33, periodSummary(steps = 1, goal = 3, days = 1).percent)
    }

    @Test
    fun zeroOrNullGoalGivesNoPercentAndEmptyBar() {
        val zero = periodSummary(steps = 5_000, goal = 0, days = 7)
        assertNull(zero.percent)
        assertNull(zero.goal)
        assertEquals(0f, zero.barFraction, 0f)

        val none = periodSummary(steps = 5_000, goal = null, days = 30)
        assertNull(none.percent)
        assertEquals(5_000.0 / 30, none.averagePerDay, 0.0001)
    }

    @Test
    fun averagePerDayDividesByDays() {
        assertEquals(100.0, periodSummary(steps = 700, goal = null, days = 7).averagePerDay, 0.0001)
        assertEquals(0.0, periodSummary(steps = 0, goal = 100, days = 7).averagePerDay, 0.0)
    }

    @Test
    fun stepsInLastDaysIncludesEndDateAndExcludesOlder() {
        val end = LocalDate.of(2026, 3, 20)
        val history = listOf(
            DailyRecord("2026-03-13", 1_000, 0.0), // outside a 7-day window
            DailyRecord("2026-03-14", 100, 0.0),
            DailyRecord("2026-03-20", 400, 0.0),
            DailyRecord("2026-03-21", 9_000, 0.0), // after end
        )
        assertEquals(500, stepsInLastDays(history, end, 7))
    }

    @Test
    fun monthToDateStartsOnFirstOfMonth() {
        val end = LocalDate.of(2026, 3, 5)
        val history = listOf(
            DailyRecord("2026-02-28", 9_000, 0.0),
            DailyRecord("2026-03-01", 200, 0.0),
            DailyRecord("2026-03-05", 300, 0.0),
        )
        assertEquals(500, stepsMonthToDate(history, end))
        assertEquals(5, daysMonthToDate(end))
        assertTrue(periodSummary(500, 10_000, daysMonthToDate(end)).averagePerDay == 100.0)
    }
}
