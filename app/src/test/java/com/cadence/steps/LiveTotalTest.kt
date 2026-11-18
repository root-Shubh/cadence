package com.cadence.steps

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

    @Test
    fun leadCapIsTenWhenCounterJustReported() {
        assertEquals(10, accelLeadCap(nowMillis = 5_000L, lastCounterMillis = 5_000L, maxStepsPerSecond = 3.5))
    }

    @Test
    fun leadCapGrowsWhileCounterIsSilent() {
        // 4 s of silence at 3.5 steps/s: 10 + 14 = 24 steps of lead allowed.
        assertEquals(24, accelLeadCap(nowMillis = 9_000L, lastCounterMillis = 5_000L, maxStepsPerSecond = 3.5))
    }

    @Test
    fun leadCapIsBoundedAtMax() {
        assertEquals(ACCEL_LEAD_CAP_MAX, accelLeadCap(nowMillis = 600_000L, lastCounterMillis = 0L, maxStepsPerSecond = 3.5))
    }

    @Test
    fun walkingWithSilentCounterKeepsCountingPastTen() {
        // 12 accelerometer steps over 6 s with no counter report: all 12 show, not frozen at 10.
        val cap = accelLeadCap(nowMillis = 6_000L, lastCounterMillis = 0L, maxStepsPerSecond = 3.5)
        assertEquals(1212, displayTotal(counterTotal = 1200, accelSteps = 12, cap = cap))
    }
}
