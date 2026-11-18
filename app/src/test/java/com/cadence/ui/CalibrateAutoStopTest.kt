package com.cadence.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrateAutoStopTest {

    @Test
    fun doesNotStopBelowFiftySteps() {
        assertFalse(shouldAutoStop(0))
        assertFalse(shouldAutoStop(49))
    }

    @Test
    fun stopsAtFiftySteps() {
        assertTrue(shouldAutoStop(50))
        assertTrue(shouldAutoStop(51))
    }

    @Test
    fun defaultTargetIsFifty() {
        assertEquals(50, CALIBRATION_TARGET_STEPS)
    }

    @Test
    fun customTargetIsUsed() {
        assertFalse(shouldAutoStop(9, targetSteps = 10))
        assertTrue(shouldAutoStop(10, targetSteps = 10))
    }
}
