package com.cadence.steps

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTextTest {

    @Test
    fun zeroStepsShowsZeroPercentAndFullRemaining() {
        val t = notificationLines(steps = 0, goal = 10_000)
        assertEquals("0 steps today", t.title)
        assertEquals("0% of 10,000 goal", t.text)
        assertEquals("10,000 steps to go", t.remainingLine)
        assertEquals(10_000, t.remaining)
    }

    @Test
    fun underGoalShowsPercentAndRemaining() {
        val t = notificationLines(steps = 2_500, goal = 10_000)
        assertEquals("2,500 steps today", t.title)
        assertEquals("25% of 10,000 goal", t.text)
        assertEquals("7,500 steps to go", t.remainingLine)
        assertEquals(7_500, t.remaining)
    }

    @Test
    fun exactlyAtGoalShowsGoalReached() {
        val t = notificationLines(steps = 10_000, goal = 10_000)
        assertEquals("100% of 10,000 goal", t.text)
        assertEquals("Goal reached", t.remainingLine)
        assertEquals(0, t.remaining)
    }

    @Test
    fun overGoalKeepsPercentUncappedAndRemainingZero() {
        val t = notificationLines(steps = 12_345, goal = 10_000)
        assertEquals("12,345 steps today", t.title)
        assertEquals("123% of 10,000 goal", t.text)
        assertEquals("Goal reached", t.remainingLine)
        assertEquals(0, t.remaining)
    }

    @Test
    fun zeroGoalUsesDefaultGoal() {
        val t = notificationLines(steps = 1_000, goal = 0)
        assertEquals(DEFAULT_DAILY_GOAL, t.goal)
        assertEquals("10% of 10,000 goal", t.text)
        assertEquals("9,000 steps to go", t.remainingLine)
    }

    @Test
    fun negativeGoalAndNegativeStepsAreGuarded() {
        val t = notificationLines(steps = -5, goal = -1)
        assertEquals(DEFAULT_DAILY_GOAL, t.goal)
        assertEquals(0, t.percent)
        assertEquals("0 steps today", t.title)
    }
}
