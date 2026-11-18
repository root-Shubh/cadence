package com.stepwise.steps

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTotalTest {

    @Test
    fun leadIsCappedAtTen() {
        assertEquals(1210, displayTotal(counterTotal = 1200, accelSteps = 25))
    }

    @Test
    fun leadIsTheUnmatchedAccelSteps() {
        assertEquals(1205, displayTotal(counterTotal = 1200, accelSteps = 5))
    }

    @Test
    fun leadIsZeroWhenNoAccelStepsAreUnmatched() {
        assertEquals(1200, displayTotal(counterTotal = 1200, accelSteps = 0))
    }

    @Test
    fun negativeAccelStepsGiveNoLead() {
        assertEquals(1200, displayTotal(counterTotal = 1200, accelSteps = -4))
    }

    @Test
    fun shakeBurstOfFiftyWithNoCounterMovementShowsOnlyTenExtra() {
        // The accelerometer saw 50 steps; the counter has not moved since 1200.
        assertEquals(1210, displayTotal(counterTotal = 1200, accelSteps = 50))
    }

    @Test
    fun customCapIsRespected() {
        assertEquals(1203, displayTotal(counterTotal = 1200, accelSteps = 50, cap = 3))
    }

    @Test
    fun totalIsClampedToIntMax() {
        assertEquals(Int.MAX_VALUE, displayTotal(counterTotal = Int.MAX_VALUE, accelSteps = 5))
    }

    @Test
    fun storedNeverGoesBackwards() {
        assertEquals(900, mergeStored(previousStored = 900, candidate = 800))
    }

    @Test
    fun storedMovesForwardToCandidate() {
        assertEquals(950, mergeStored(previousStored = 900, candidate = 950))
    }

    @Test
    fun storedKeepsPreviousWhenEqual() {
        assertEquals(900, mergeStored(previousStored = 900, candidate = 900))
    }
}
