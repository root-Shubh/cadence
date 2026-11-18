package com.cadence.ui

import com.cadence.domain.DailyRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class InsightsTest {

    private val end = LocalDate.of(2026, 11, 18)
    private val endIso = end.toString()

    private fun rec(date: LocalDate, steps: Int, kcal: Double = steps * 0.04) =
        DailyRecord(date.toString(), steps, kcal)

    /** Records for [count] days ending on [end], steps given by [stepsFor]. */
    private fun history(count: Int, stepsFor: (LocalDate) -> Int): List<DailyRecord> =
        (0 until count).map { offset ->
            val date = end.minusDays(offset.toLong())
            rec(date, stepsFor(date))
        }

    private fun List<Insight>.kinds() = map { it.kind }.toSet()

    private fun List<Insight>.of(kind: InsightKind) = filter { it.kind == kind }

    // ---- empty and missing-goal handling -------------------------------------------------

    @Test
    fun emptyHistoryNeverThrowsAndOnlyGivesAdviceAndGoalStart() {
        val result = buildInsights(emptyList(), endIso, dailyGoal = 8000, weeklyGoal = 50000)
        assertEquals(setOf(InsightKind.ADVICE, InsightKind.GOAL_PACE), result.kinds())
        assertEquals("Start tracking", result.first().title)
        assertTrue(result.of(InsightKind.GOAL_PACE).single().body.contains("Steps are needed"))
    }

    @Test
    fun emptyHistoryWithoutGoalsGivesOnlyAdvice() {
        val result = buildInsights(emptyList(), endIso, dailyGoal = null, weeklyGoal = null)
        assertEquals(listOf(InsightKind.ADVICE), result.map { it.kind })
    }

    @Test
    fun invalidEndDateGivesNoInsights() {
        assertTrue(buildInsights(history(10) { 5000 }, "not-a-date", 8000, 50000).isEmpty())
    }

    @Test
    fun missingGoalsProduceNoStreakOrGoalPaceCards() {
        val result = buildInsights(history(30) { 9000 }, endIso, dailyGoal = null, weeklyGoal = null)
        assertFalse(InsightKind.STREAK in result.kinds())
        assertFalse(InsightKind.GOAL_PACE in result.kinds())
    }

    @Test
    fun nonPositiveGoalsAreTreatedAsMissing() {
        val result = buildInsights(history(30) { 9000 }, endIso, dailyGoal = 0, weeklyGoal = -5)
        assertFalse(InsightKind.STREAK in result.kinds())
        assertFalse(InsightKind.GOAL_PACE in result.kinds())
    }

    @Test
    fun adviceIsAlwaysOneOrTwoItems() {
        listOf(emptyList(), history(3) { 1000 }, history(60) { (it.dayOfMonth * 311) % 9000 }).forEach { h ->
            val advice = buildInsights(h, endIso, 8000, 50000).of(InsightKind.ADVICE)
            assertTrue(advice.size in 1..2)
        }
    }

    // ---- a. weekday pattern --------------------------------------------------------------

    @Test
    fun weekdayPatternNeedsFourteenDays() {
        assertNull(weekdayPatternInsight(indexByDate(history(13) { 8000 }), end))
    }

    @Test
    fun weekdayPatternNamesMostAndLeastActiveAndWeekendGap() {
        val perDay = mapOf(
            DayOfWeek.MONDAY to 6000, DayOfWeek.TUESDAY to 7000, DayOfWeek.WEDNESDAY to 8000,
            DayOfWeek.THURSDAY to 9000, DayOfWeek.FRIDAY to 10000,
            DayOfWeek.SATURDAY to 4000, DayOfWeek.SUNDAY to 3000,
        )
        val insight = weekdayPatternInsight(indexByDate(history(56) { perDay.getValue(it.dayOfWeek) }), end)
        assertNotNull(insight)
        assertTrue(insight!!.body.contains("Most active on Friday"))
        assertTrue(insight.body.contains("least active on Sunday"))
        assertTrue(insight.body.contains("-56."))
        assertEquals(InsightTone.NEUTRAL, insight.tone)
    }

    @Test
    fun weekendChangeIsNullWithoutWeekendData() {
        val weekdaysOnly = history(56) { 8000 }.filter { LocalDate.parse(it.dateIso).dayOfWeek.value <= 5 }
        assertNull(weekendChangePercent(indexByDate(weekdaysOnly), end))
    }

    // ---- b. best day ---------------------------------------------------------------------

    @Test
    fun bestDayIsHighestWithinThirtyDays() {
        val h = listOf(rec(end.minusDays(3), 12345), rec(end.minusDays(40), 99999), rec(end, 4000))
        val insight = bestDayInsight(indexByDate(h), end)
        assertNotNull(insight)
        assertTrue(insight!!.body.contains("12,345 steps"))
        assertTrue(insight.body.contains(end.minusDays(3).toString().let { dayLabelLong(it) }))
    }

    @Test
    fun bestDayIgnoresZeroOnlyDays() {
        assertNull(bestDayInsight(indexByDate(history(5) { 0 }), end))
    }

    // ---- c. streak -----------------------------------------------------------------------

    @Test
    fun streakCountsFromTodayWhenTodayMeetsGoal() {
        val h = history(4) { 9000 }
        assertEquals(StreakStats(current = 4, longest = 4), goalStreaks(indexByDate(h), end, 8000))
    }

    @Test
    fun streakCanEndYesterday() {
        val h = listOf(rec(end, 100)) + history(4) { 9000 }.filter { it.dateIso != endIso }
        assertEquals(3, goalStreaks(indexByDate(h), end, 8000).current)
    }

    @Test
    fun streakBreaksOnMissedDayAndLongestIsKept() {
        val h = listOf(rec(end, 100), rec(end.minusDays(1), 100)) +
            (3L..7L).map { rec(end.minusDays(it), 9000) }
        val stats = goalStreaks(indexByDate(h), end, 8000)
        assertEquals(0, stats.current)
        assertEquals(5, stats.longest)
    }

    @Test
    fun streakInsightShowsCurrentAndLongest() {
        val insight = streakInsight(StreakStats(current = 2, longest = 6), 8000)
        assertNotNull(insight)
        assertTrue(insight!!.body.contains("2-day streak"))
        assertTrue(insight.body.contains("6 days"))
        assertEquals(InsightTone.POSITIVE, insight.tone)
    }

    @Test
    fun streakInsightIsNullWhenNothingToSay() {
        assertNull(streakInsight(StreakStats(0, 0), 8000))
    }

    // ---- d. trend ------------------------------------------------------------------------

    @Test
    fun trendUpIsPositiveWithPercentage() {
        val h = history(7) { 10000 } + (7L..13L).map { rec(end.minusDays(it), 8000) }
        val insight = trendInsight(indexByDate(h), end)
        assertNotNull(insight)
        assertEquals(InsightTone.POSITIVE, insight!!.tone)
        assertTrue(insight.body.contains("+25.0%"))
    }

    @Test
    fun trendDownIsNudge() {
        val h = history(7) { 8000 } + (7L..13L).map { rec(end.minusDays(it), 10000) }
        val insight = trendInsight(indexByDate(h), end)
        assertEquals(InsightTone.NUDGE, insight!!.tone)
        assertTrue(insight.body.contains("-20.0%"))
    }

    @Test
    fun trendUnderThreePercentIsNeutral() {
        val h = history(7) { 10100 } + (7L..13L).map { rec(end.minusDays(it), 10000) }
        assertEquals(InsightTone.NEUTRAL, trendInsight(indexByDate(h), end)!!.tone)
    }

    @Test
    fun trendNeedsBothWindows() {
        assertNull(trendInsight(indexByDate(history(7) { 9000 }), end))
    }

    // ---- e. goal pace --------------------------------------------------------------------

    @Test
    fun goalPaceShowsWeeklyTextFromGoalsMaths() {
        // Window total 14000 over 2 days, goal 21000: deficit 7000 at 7000 a day, so 1 more day.
        val h = listOf(rec(end, 7000), rec(end.minusDays(1), 7000))
        val insight = goalPaceInsight(indexByDate(h), end, dailyGoal = null, weeklyGoal = 21000)
        assertNotNull(insight)
        assertTrue(insight!!.body.contains("1 more day reaches this week's 21,000 goal"))
    }

    @Test
    fun goalPaceSaysStepsRemainingWhenTodayBelowDailyGoal() {
        val insight = goalPaceInsight(indexByDate(listOf(rec(end, 3000))), end, dailyGoal = 8000, weeklyGoal = null)
        assertNotNull(insight)
        assertTrue(insight!!.body.contains("5 steps") || insight.body.contains("5,000 steps"))
    }

    @Test
    fun goalPaceIsNullWithoutGoals() {
        assertNull(goalPaceInsight(indexByDate(history(3) { 1 }), end, null, null))
    }

    // ---- f. consistency ------------------------------------------------------------------

    @Test
    fun consistencyCountsActiveDaysAndLongestRun() {
        // Active on the last 3 days and on day 10 back; day 3 back has zero steps.
        val h = listOf(
            rec(end, 500), rec(end.minusDays(1), 500), rec(end.minusDays(2), 500),
            rec(end.minusDays(3), 0), rec(end.minusDays(10), 500),
        )
        val insight = consistencyInsight(indexByDate(h), end)
        assertNotNull(insight)
        assertTrue(insight!!.body.contains("on 4 of the last 30 days"))
        assertTrue(insight.body.contains("Longest run of active days: 3 days"))
    }

    @Test
    fun consistencyIsNullWithoutActiveDays() {
        assertNull(consistencyInsight(indexByDate(history(5) { 0 }), end))
    }

    // ---- g. calories ---------------------------------------------------------------------

    @Test
    fun caloriesAveragesLastSevenDaysAndStatesAssumption() {
        val recent = history(7) { 8000 }.map { it.copy(totalKcal = 2000.0) }
        val older = rec(end.minusDays(9), 1000, kcal = 9999.0)
        val insight = caloriesInsight(indexByDate(recent + older), end)
        assertNotNull(insight)
        assertTrue(insight!!.body.contains("about 2,000 kcal a day"))
        assertTrue(insight.body.contains("About 1,000 extra steps a day burns an extra 100 kcal"))
        assertTrue(insight.body.contains("average stride"))
    }

    @Test
    fun caloriesIsNullWithoutRecentData() {
        assertNull(caloriesInsight(indexByDate(listOf(rec(end.minusDays(20), 5000))), end))
    }

    @Test
    fun hundredKcalIsAboutOneThousandStepsAtDefaultRate() {
        assertEquals(0.1, KCAL_PER_STEP, 0.0)
        assertEquals(1000L, stepsForExtraKcal(100.0))
        assertEquals(0L, stepsForExtraKcal(0.0))
        assertEquals(2000L, stepsForExtraKcal(200.0, kcalPerStep = 0.1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun stepsForExtraKcalRejectsNonPositiveRate() {
        stepsForExtraKcal(100.0, kcalPerStep = 0.0)
    }

    // ---- h. advice -----------------------------------------------------------------------

    @Test
    fun adviceStartTrackingWhenNoDataInLastThreeDays() {
        val advice = adviceInsights(indexByDate(listOf(rec(end.minusDays(5), 9000))), end, null)
        assertEquals("Start tracking", advice.first().title)
    }

    @Test
    fun adviceShortWalkWhenLastThreeDaysBelowAverage() {
        val h = (3L..6L).map { rec(end.minusDays(it), 9000) } + (0L..2L).map { rec(end.minusDays(it), 2000) }
        val advice = adviceInsights(indexByDate(h), end, null)
        assertTrue(advice.any { it.title == "Short walk today" })
        assertFalse(advice.any { it.title == "Keep the streak" })
    }

    @Test
    fun adviceWeekendWalkWhenWeekendsMuchLower() {
        val h = history(56) { date -> if (date.dayOfWeek.value >= 6) 3000 else 9000 }
        assertTrue(adviceInsights(indexByDate(h), end, null).any { it.title == "Weekend walk" })
    }

    @Test
    fun adviceEncouragesActiveStreak() {
        val advice = adviceInsights(indexByDate(history(3) { 9000 }), end, streakCurrent = 3)
        assertEquals("Keep the streak", advice.single().title)
        assertEquals(InsightTone.POSITIVE, advice.single().tone)
    }

    @Test
    fun adviceFallsBackToSteadyRhythm() {
        val advice = adviceInsights(indexByDate(history(3) { 9000 }), end, null)
        assertEquals(listOf("Steady rhythm"), advice.map { it.title })
    }

    @Test
    fun adviceNeverExceedsTwoItems() {
        val h = (3L..30L).map { rec(end.minusDays(it), 2000) }
        assertTrue(adviceInsights(indexByDate(h), end, streakCurrent = 2).size <= 2)
    }

    // ---- sample history ------------------------------------------------------------------

    @Test
    fun sampleHistoryProducesExpectedKinds() {
        val h = history(60) { date -> if (date.dayOfWeek.value >= 6) 5000 else 9000 + date.dayOfMonth * 10 }
        val result = buildInsights(h, endIso, dailyGoal = 8000, weeklyGoal = 56000)
        assertTrue(
            result.kinds().containsAll(
                setOf(
                    InsightKind.ADVICE, InsightKind.GOAL_PACE, InsightKind.STREAK, InsightKind.TREND,
                    InsightKind.WEEKDAY_PATTERN, InsightKind.BEST_DAY, InsightKind.CONSISTENCY,
                    InsightKind.CALORIES,
                ),
            ),
        )
        assertEquals(InsightKind.ADVICE, result.first().kind)
    }
}
