package com.cadence.steps

import org.junit.Assert.assertEquals
import org.junit.Test

class RingIconTest {

    @Test
    fun sweepIsProportionalToPercent() {
        assertEquals(0f, ringSweepDegrees(0), 0.001f)
        assertEquals(90f, ringSweepDegrees(25), 0.001f)
        assertEquals(360f, ringSweepDegrees(100), 0.001f)
    }

    @Test
    fun sweepIsClampedToFullRing() {
        assertEquals(360f, ringSweepDegrees(250), 0.001f)
        assertEquals(0f, ringSweepDegrees(-10), 0.001f)
    }

    @Test
    fun centreTextShowsClampedPercent() {
        assertEquals("42%", ringCenterText(42))
        assertEquals("0%", ringCenterText(-3))
    }
}
