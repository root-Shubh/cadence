package com.cadence.goals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EffectiveGoalsTest {

    @Test
    fun dailyDerivesWeeklyAndMonthly() {
        val result = effectiveGoals(Goals(dailySteps = 8000))
        assertEquals(56_000, result.weeklySteps)
        assertEquals(240_000, result.monthlySteps)
        assertEquals(8000, result.dailySteps)
    }

    @Test
    fun explicitWeeklyWinsOverDerivedWeekly() {
        val result = effectiveGoals(Goals(dailySteps = 8000, weeklySteps = 50_000))
        assertEquals(50_000, result.weeklySteps)
        assertEquals(240_000, result.monthlySteps)
    }

    @Test
    fun explicitMonthlyWinsOverDerivedMonthly() {
        val result = effectiveGoals(Goals(dailySteps = 8000, monthlySteps = 200_000))
        assertEquals(56_000, result.weeklySteps)
        assertEquals(200_000, result.monthlySteps)
    }

    @Test
    fun explicitValuesWinWhenAllSet() {
        val goals = Goals(dailySteps = 8000, weeklySteps = 50_000, monthlySteps = 200_000)
        assertEquals(goals, effectiveGoals(goals))
    }

    @Test
    fun weeklyOnlyDerivesDailyAndMonthly() {
        val result = effectiveGoals(Goals(weeklySteps = 56_000))
        assertEquals(8000, result.dailySteps)
        assertEquals(56_000, result.weeklySteps)
        assertEquals(240_000, result.monthlySteps)
    }

    @Test
    fun monthlyOnlyDerivesDailyAndWeekly() {
        val result = effectiveGoals(Goals(monthlySteps = 240_000))
        assertEquals(8000, result.dailySteps)
        assertEquals(56_000, result.weeklySteps)
        assertEquals(240_000, result.monthlySteps)
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
