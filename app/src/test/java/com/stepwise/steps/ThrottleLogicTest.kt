package com.stepwise.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThrottleLogicTest {

    @Test
    fun writesWhenNoWriteYet() {
        assertTrue(shouldWriteNow(lastWriteMillis = 0L, nowMillis = 5_000L))
    }

    @Test
    fun blocksWriteInsideTheInterval() {
        assertFalse(shouldWriteNow(lastWriteMillis = 10_000L, nowMillis = 10_000L))
        assertFalse(shouldWriteNow(lastWriteMillis = 10_000L, nowMillis = 11_999L))
    }

    @Test
    fun allowsWriteAtExactlyTheInterval() {
        assertTrue(shouldWriteNow(lastWriteMillis = 10_000L, nowMillis = 12_000L))
        assertTrue(shouldWriteNow(lastWriteMillis = 10_000L, nowMillis = 15_000L))
    }

    @Test
    fun defaultIntervalIsTwoSeconds() {
        assertEquals(2000L, STORE_MIN_INTERVAL_MS)
        assertTrue(shouldWriteNow(0L, 2000L))
        assertFalse(shouldWriteNow(0L, 1999L))
    }

    @Test
    fun customIntervalIsUsed() {
        assertFalse(shouldWriteNow(0L, 500L, minIntervalMillis = 1000L))
        assertTrue(shouldWriteNow(0L, 1000L, minIntervalMillis = 1000L))
    }

    @Test
    fun clockGoingBackwardsDoesNotBlockWrites() {
        assertTrue(shouldWriteNow(lastWriteMillis = 10_000L, nowMillis = 4_000L))
    }

    @Test
    fun progressIsStepsWithinGoal() {
        assertEquals(2500, progressFor(steps = 2500, goal = 10000))
        assertEquals(0, progressFor(steps = 0, goal = 10000))
    }

    @Test
    fun progressIsClampedToGoal() {
        assertEquals(10000, progressFor(steps = 12000, goal = 10000))
        assertEquals(10000, progressFor(steps = 10000, goal = 10000))
    }

    @Test
    fun progressIsZeroForNegativeSteps() {
        assertEquals(0, progressFor(steps = -5, goal = 10000))
    }

    @Test
    fun progressIsZeroForNonPositiveGoal() {
        assertEquals(0, progressFor(steps = 500, goal = 0))
        assertEquals(0, progressFor(steps = 500, goal = -1))
    }

    @Test
    fun defaultGoalIsTenThousand() {
        assertEquals(10_000, DEFAULT_DAILY_GOAL)
    }
}
