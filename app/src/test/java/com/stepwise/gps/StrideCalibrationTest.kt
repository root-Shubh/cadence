package com.stepwise.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrideCalibrationTest {

    @Test
    fun successComputesStrideInCentimetres() {
        val result = strideFromWalk(distanceM = 100.0, steps = 125)
        assertTrue(result is StrideResult.Success)
        assertEquals(80.0, (result as StrideResult.Success).strideCm, 1e-9)
    }

    @Test
    fun tooShortDistanceIsRejected() {
        val result = strideFromWalk(distanceM = 49.9, steps = 100)
        assertTrue(result is StrideResult.Rejected)
        assertTrue((result as StrideResult.Rejected).reason.contains("too short"))
    }

    @Test
    fun tooFewStepsIsRejected() {
        val result = strideFromWalk(distanceM = 100.0, steps = 49)
        assertTrue(result is StrideResult.Rejected)
        assertTrue((result as StrideResult.Rejected).reason.contains("too few steps"))
    }

    @Test
    fun implausibleStrideIsRejected() {
        // 100 m over 50 steps is 200 cm per step.
        val tooLong = strideFromWalk(distanceM = 100.0, steps = 50)
        assertEquals(StrideResult.Rejected("implausible stride"), tooLong)

        // 50 m over 200 steps is 25 cm per step.
        val tooShort = strideFromWalk(distanceM = 50.0, steps = 200)
        assertEquals(StrideResult.Rejected("implausible stride"), tooShort)
    }

    @Test
    fun boundaryValuesAreAccepted() {
        val low = strideFromWalk(distanceM = 60.0, steps = 200) // 30 cm
        val high = strideFromWalk(distanceM = 120.0, steps = 100) // 120 cm
        assertEquals(30.0, (low as StrideResult.Success).strideCm, 1e-9)
        assertEquals(120.0, (high as StrideResult.Success).strideCm, 1e-9)
    }
}
