package com.stepwise.goals

import com.stepwise.goals.GoalSuggestion.DailyRemaining
import com.stepwise.goals.GoalSuggestion.Met
import com.stepwise.goals.GoalSuggestion.Missed
import com.stepwise.goals.GoalSuggestion.MonthlyExtraPerDay
import com.stepwise.goals.GoalSuggestion.WeeklyDaysNeeded
import com.stepwise.goals.GoalSuggestion.WeeklyStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class GoalsTest {

    // ---- Goals validation ----

    @Test
    fun goalsAllowUnsetAndPositiveValues() {
        val goals = Goals()
        assertNull(goals.dailySteps)
        assertNull(goals.weeklySteps)
        assertNull(goals.monthlySteps)
        assertEquals(8000, Goals(dailySteps = 8000).dailySteps)
    }

    @Test
    fun goalsRejectZero() {
        assertThrows(IllegalArgumentException::class.java) { Goals(dailySteps = 0) }
        assertThrows(IllegalArgumentException::class.java) { Goals(weeklySteps = 0) }
        assertThrows(IllegalArgumentException::class.java) { Goals(monthlySteps = 0) }
    }

    @Test
    fun goalsRejectNegative() {
        assertThrows(IllegalArgumentException::class.java) { Goals(dailySteps = -1) }
        assertThrows(IllegalArgumentException::class.java) { Goals(weeklySteps = -1) }
        assertThrows(IllegalArgumentException::class.java) { Goals(monthlySteps = -1) }
    }

    @Test
    fun stepPointRejectsNegativeSteps() {
        assertThrows(IllegalArgumentException::class.java) { StepPoint("2026-03-10", -5) }
    }

    @Test
    fun stepPointRejectsBadDate() {
        assertThrows(IllegalArgumentException::class.java) { StepPoint("10/03/2026", 100) }
        assertThrows(IllegalArgumentException::class.java) { StepPoint("2026-02-30", 100) }
    }

    // ---- Daily ----

    @Test
    fun dailyMetWhenStepsReachGoal() {
        val p = dailyProgress(
            listOf(StepPoint("2026-03-10", 8000)),
            Goals(dailySteps = 8000),
            "2026-03-10",
        )!!
        assertTrue(p.met)
        assertEquals(0, p.deficit)
    }

    @Test
    fun dailyNotMetReportsDeficit() {
        val p = dailyProgress(
            listOf(StepPoint("2026-03-10", 3000)),
            Goals(dailySteps = 8000),
            "2026-03-10",
        )!!
        assertFalse(p.met)
        assertEquals(5000, p.deficit)
    }

    @Test
    fun dailyNoRowCountsAsZero() {
        val p = dailyProgress(
            listOf(StepPoint("2026-03-09", 9000)),
            Goals(dailySteps = 8000),
            "2026-03-10",
        )!!
        assertEquals(0, p.steps)
        assertEquals(8000, p.deficit)
        assertFalse(p.met)
    }

    @Test
    fun dailyNullWhenNoGoalSet() {
        assertNull(dailyProgress(emptyList(), Goals(), "2026-03-10"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun dailyRejectsBadEndDate() {
        dailyProgress(emptyList(), Goals(dailySteps = 8000), "2026-13-01")
    }

    // ---- Weekly ----

    @Test
    fun weeklyWindowIsSevenDaysInclusive() {
        // Window for end 2026-03-10 is 2026-03-04 .. 2026-03-10.
        val points = listOf(
            StepPoint("2026-03-03", 99_999), // day 7 back: excluded
            StepPoint("2026-03-04", 1_000),  // day 6 back: included
            StepPoint("2026-03-10", 2_000),  // end date: included
            StepPoint("2026-03-11", 50_000), // after end: excluded
        )
        val p = weeklyProgress(points, Goals(weeklySteps = 50_000), "2026-03-10")!!
        assertEquals(3_000, p.steps)
        assertEquals(47_000, p.deficit)
        assertEquals(java.time.LocalDate.of(2026, 3, 4), p.windowStart)
    }

    @Test
    fun weeklyCountsDistinctDaysWithData() {
        val points = listOf(
            StepPoint("2026-03-05", 1_000),
            StepPoint("2026-03-05", 500), // same day, second row
            StepPoint("2026-03-08", 1_000),
        )
        val p = weeklyProgress(points, Goals(weeklySteps = 50_000), "2026-03-10")!!
        assertEquals(2, p.daysWithData)
        assertEquals(2_500, p.steps)
    }

    @Test
    fun weeklyAverageUsesOnlyDaysWithData() {
        val points = listOf(
            StepPoint("2026-03-04", 6_000),
            StepPoint("2026-03-09", 4_000),
        )
        val p = weeklyProgress(points, Goals(weeklySteps = 20_000), "2026-03-10")!!
        // Average 5,000 over 2 days with data, not over 7 days.
        val s = weeklySuggestion(p.goal, p.steps, p.daysWithData)
        assertEquals(WeeklyDaysNeeded(daysNeeded = 2, averagePerDay = 5_000, goal = 20_000), s)
    }

    @Test
    fun weeklyDaysNeededRoundsUp() {
        // Deficit 4,000 at 1,500 a day is 2.67 days, so 3.
        assertEquals(
            WeeklyDaysNeeded(daysNeeded = 3, averagePerDay = 1_500, goal = 10_000),
            weeklySuggestion(goal = 10_000, windowTotal = 6_000, daysWithData = 4),
        )
    }

    @Test
    fun weeklyNoDataGivesStartSuggestion() {
        val p = weeklyProgress(emptyList(), Goals(weeklySteps = 50_000), "2026-03-10")!!
        assertEquals(0, p.daysWithData)
        val s = weeklySuggestion(p.goal, p.steps, p.daysWithData)
        assertEquals(WeeklyStart(50_000), s)
        assertEquals("Steps are needed to start toward this week's 50,000 goal.", s.text())
    }

    @Test
    fun weeklyGoalMetGivesMet() {
        val points = listOf(StepPoint("2026-03-10", 60_000))
        val p = weeklyProgress(points, Goals(weeklySteps = 50_000), "2026-03-10")!!
        assertTrue(p.met)
        assertEquals(Met, weeklySuggestion(p.goal, p.steps, p.daysWithData))
    }

    @Test
    fun weeklyDaysLeftIsZeroForRollingWindow() {
        val p = weeklyProgress(emptyList(), Goals(weeklySteps = 1), "2026-03-10")!!
        assertEquals(0, p.daysLeft)
    }

    @Test
    fun weeklyNullWhenNoGoalSet() {
        assertNull(weeklyProgress(emptyList(), Goals(), "2026-03-10"))
    }

    // ---- Monthly ----

    @Test
    fun februaryInNonLeapYear() {
        val points = listOf(
            StepPoint("2026-01-31", 9_999), // previous month: excluded
            StepPoint("2026-02-01", 1_000),
            StepPoint("2026-02-14", 2_000),
            StepPoint("2026-02-20", 7_000), // after end date: excluded
        )
        val p = monthlyProgress(points, Goals(monthlySteps = 100_000), "2026-02-14")!!
        assertEquals(3_000, p.steps)
        assertEquals(15, p.daysLeftInMonth) // 14th through 28th inclusive
        assertEquals(java.time.YearMonth.of(2026, 2), p.month)
    }

    @Test
    fun lastDayOfFebruaryHasOneDayLeft() {
        val p = monthlyProgress(emptyList(), Goals(monthlySteps = 100_000), "2026-02-28")!!
        assertEquals(1, p.daysLeftInMonth)
    }

    @Test
    fun thirtyOneDayMonthCounts() {
        val first = monthlyProgress(emptyList(), Goals(monthlySteps = 1), "2026-01-01")!!
        val last = monthlyProgress(emptyList(), Goals(monthlySteps = 1), "2026-01-31")!!
        assertEquals(31, first.daysLeftInMonth)
        assertEquals(1, last.daysLeftInMonth)
    }

    @Test
    fun remainingDaysIncludeToday() {
        val p = monthlyProgress(emptyList(), Goals(monthlySteps = 1), "2026-03-20")!!
        assertEquals(12, p.daysLeftInMonth) // 20th through 31st inclusive
    }

    @Test
    fun monthlyMetWhenTotalReachesGoal() {
        val points = listOf(StepPoint("2026-03-02", 250_000))
        val p = monthlyProgress(points, Goals(monthlySteps = 250_000), "2026-03-20")!!
        assertTrue(p.met)
        assertEquals(0, p.deficit)
        assertEquals(Met, monthlySuggestion(p.goal, p.steps, p.daysLeftInMonth))
    }

    @Test
    fun monthlyEndOfMonthShortfallIsMissed() {
        val s = monthlySuggestion(goal = 250_000, monthTotal = 248_800, remainingDays = 0)
        assertEquals(Missed(shortfall = 1_200, goal = 250_000), s)
        assertEquals("The month ended 1,200 steps short of 250,000.", s.text())
    }

    @Test
    fun monthlyExtraPerDayRoundsUp() {
        // Deficit 10 over 3 remaining days is 3.33, so 4.
        assertEquals(
            MonthlyExtraPerDay(extraPerDay = 4, goal = 100),
            monthlySuggestion(goal = 100, monthTotal = 90, remainingDays = 3),
        )
    }

    @Test
    fun monthlyNullWhenNoGoalSet() {
        assertNull(monthlyProgress(emptyList(), Goals(), "2026-03-20"))
    }

    // ---- Daily / weekly suggestions ----

    @Test
    fun dailySuggestionMetAndRemaining() {
        assertEquals(Met, dailySuggestion(goal = 8_000, steps = 8_000))
        assertEquals(DailyRemaining(1_200), dailySuggestion(goal = 8_000, steps = 6_800))
    }

    // ---- Text output ----

    @Test
    fun weeklyDaysNeededTextExact() {
        val s = WeeklyDaysNeeded(daysNeeded = 3, averagePerDay = 6_500, goal = 50_000)
        assertEquals(
            "At your average of 6,500 a day, 3 more days reach this week's 50,000 goal.",
            s.text(),
        )
    }

    @Test
    fun weeklyDaysNeededSingularTextExact() {
        val s = WeeklyDaysNeeded(daysNeeded = 1, averagePerDay = 6_500, goal = 50_000)
        assertEquals(
            "At your average of 6,500 a day, 1 more day reaches this week's 50,000 goal.",
            s.text(),
        )
    }

    @Test
    fun monthlyExtraPerDayTextExact() {
        assertEquals(
            "Walk 2,100 more steps a day for the rest of the month to reach 250,000.",
            MonthlyExtraPerDay(extraPerDay = 2_100, goal = 250_000).text(),
        )
    }

    @Test
    fun dailyRemainingTextExact() {
        assertEquals(
            "You are 1,200 steps from today's goal.",
            DailyRemaining(deficit = 1_200).text(),
        )
    }

    @Test
    fun metAndMissedTextExact() {
        assertEquals("Goal met.", Met.text())
        assertEquals(
            "The month ended 1 step short of 250,000.",
            Missed(shortfall = 1, goal = 250_000).text(),
        )
    }

    @Test
    fun textUsesThousandsSeparators() {
        val text = dailySuggestion(goal = 10_000, steps = 0).text()
        assertEquals("You are 10,000 steps from today's goal.", text)
    }

    @Test
    fun textUsesCommaSeparatorUnderGermanDefaultLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val text = WeeklyDaysNeeded(daysNeeded = 3, averagePerDay = 6_500, goal = 50_000).text()
            assertEquals(
                "At your average of 6,500 a day, 3 more days reach this week's 50,000 goal.",
                text,
            )
            assertTrue(text.contains("6,500"))
            assertFalse(text.contains("6.500"))
            assertFalse(text.contains("50.000"))
        } finally {
            Locale.setDefault(original)
        }
    }

}
