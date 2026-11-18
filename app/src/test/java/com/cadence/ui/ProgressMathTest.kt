package com.cadence.ui

import com.cadence.domain.DailyRecord
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressMathTest {

    private val end = LocalDate.of(2026, 11, 18)

    @Test
    fun fractionIsSharePerGoal() {
        assertEquals(0.5f, progressFraction(5_000, 10_000), 0.0001f)
    }

    @Test
    fun fractionIsClampedToOneWhenOverGoal() {
        assertEquals(1f, progressFraction(15_000, 10_000), 0f)
    }

    @Test
    fun fractionIsZeroWhenNoSteps() {
        assertEquals(0f, progressFraction(0, 10_000), 0f)
    }

    @Test
    fun fractionIsZeroForNonPositiveGoal() {
        assertEquals(0f, progressFraction(5_000, 0), 0f)
        assertEquals(0f, progressFraction(5_000, -3), 0f)
    }

    @Test
    fun labelUsesThousandsSeparators() {
        assertEquals("1,250 of 10,000 steps", progressLabel(1_250, 10_000))
    }

    @Test
    fun labelWithoutSeparatorsForSmallNumbers() {
        assertEquals("0 of 500 steps", progressLabel(0, 500))
    }

    @Test
    fun weekTotalCoversSevenDaysEndingOnEndDate() {
        val history = listOf(
            DailyRecord("2026-11-11", 100, 0.0), // outside the window
            DailyRecord("2026-11-13", 200, 0.0), // first day of window
            DailyRecord("2026-11-18", 300, 0.0), // end date
        )
        assertEquals(500, windowStepTotal(history, end, WEEK_WINDOW_DAYS))
    }

    @Test
    fun weekTotalIgnoresBadDates() {
        val history = listOf(DailyRecord("not-a-date", 900, 0.0), DailyRecord("2026-11-17", 40, 0.0))
        assertEquals(40, windowStepTotal(history, end, WEEK_WINDOW_DAYS))
    }

    @Test
    fun monthTotalIncludesOnlyCurrentMonthUpToEndDate() {
        val history = listOf(
            DailyRecord("2026-10-31", 700, 0.0),
            DailyRecord("2026-11-01", 100, 0.0),
            DailyRecord("2026-11-18", 250, 0.0),
            DailyRecord("2026-11-30", 999, 0.0), // future day in the month
        )
        assertEquals(350, monthStepTotal(history, end))
    }

    @Test
    fun emptyHistoryTotalsAreZero() {
        assertEquals(0, windowStepTotal(emptyList(), end, WEEK_WINDOW_DAYS))
        assertEquals(0, monthStepTotal(emptyList(), end))
    }
}
