package com.stepwise.goals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EffectiveGoalsTest {

    @Test
    fun weeklyAndMonthlyDerivedFromDaily() {
        val result = effectiveGoals(Goals(dailySteps = 8000))
        assertEquals(56_000, result.weeklySteps)
        assertEquals(240_000, result.monthlySteps)
        assertEquals(8000, result.dailySteps)
    }

    @Test
    fun explicitWeeklyWinsOverDerived() {
        val result = effectiveGoals(Goals(dailySteps = 8000, weeklySteps = 50_000))
        assertEquals(50_000, result.weeklySteps)
        assertEquals(240_000, result.monthlySteps)
    }

    @Test
    fun explicitMonthlyWinsOverDerived() {
        val result = effectiveGoals(Goals(dailySteps = 8000, monthlySteps = 200_000))
        assertEquals(56_000, result.weeklySteps)
        assertEquals(200_000, result.monthlySteps)
    }

    @Test
    fun explicitValuesWinWhenBothSet() {
        val goals = Goals(dailySteps = 8000, weeklySteps = 1, monthlySteps = 2)
        assertEquals(goals, effectiveGoals(goals))
    }

    @Test
    fun nullDailyLeavesWeeklyAndMonthlyAsSet() {
        val result = effectiveGoals(Goals(weeklySteps = 40_000))
        assertNull(result.dailySteps)
        assertEquals(40_000, result.weeklySteps)
        assertNull(result.monthlySteps)
    }

    @Test
    fun allNullStaysNull() {
        assertEquals(Goals(), effectiveGoals(Goals()))
    }

    @Test
    fun derivedValueSaturatesInsteadOfOverflowing() {
        val result = effectiveGoals(Goals(dailySteps = Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, result.weeklySteps)
        assertEquals(Int.MAX_VALUE, result.monthlySteps)
    }
}
