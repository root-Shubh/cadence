package com.cadence.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFromDetectorTest {

    @Test
    fun addsDetectedStepsToBase() {
        assertEquals(1_250, liveFromDetector(1_200, 50L))
    }

    @Test
    fun zeroDetectedGivesBase() {
        assertEquals(800, liveFromDetector(800, 0L))
    }

    @Test
    fun negativeInputsAreClamped() {
        assertEquals(0, liveFromDetector(-5, -3L))
        assertEquals(10, liveFromDetector(10, -1L))
    }

    @Test
    fun clampsToIntMax() {
        assertEquals(Int.MAX_VALUE, liveFromDetector(Int.MAX_VALUE, 1_000L))
    }

    @Test
    fun neverBelowStoredBase() {
        for (detected in 0L..500L) {
            assertTrue(liveFromDetector(4_321, detected) >= 4_321)
        }
    }

    @Test
    fun growsByExactlyOnePerEvent() {
        val base = 9_000
        var previous = liveFromDetector(base, 0L)
        for (event in 1L..200L) {
            val now = liveFromDetector(base, event)
            assertEquals(previous + 1, now)
            previous = now
        }
        assertEquals(base + 200, previous)
    }

    @Test
    fun onlyOneValueCountsAsStep() {
        assertTrue(isDetectedStep(1.0f))
        assertTrue(!isDetectedStep(0.0f))
        assertTrue(!isDetectedStep(2.0f))
    }
}
