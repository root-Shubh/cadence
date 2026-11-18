package com.cadence.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrateAutoStopTest {

    @Test
    fun doesNotStopBelowFiftySteps() {
        assertFalse(shouldAutoStop(0, 100.0))
        assertFalse(shouldAutoStop(49, 100.0))
    }

    @Test
    fun keepsWalkingUntilFiftyMetresEvenAtFiftySteps() {
        assertFalse(shouldAutoStop(50, 35.0))
        assertFalse(shouldAutoStop(60, 49.9))
    }

    @Test
    fun stopsWhenBothStepsAndDistanceReached() {
        assertTrue(shouldAutoStop(50, 50.0))
        assertTrue(shouldAutoStop(51, 60.0))
    }

    @Test
    fun defaultTargetIsFifty() {
        assertEquals(50, CALIBRATION_TARGET_STEPS)
    }

    @Test
    fun customTargetIsUsed() {
        assertFalse(shouldAutoStop(9, 100.0, targetSteps = 10))
        assertTrue(shouldAutoStop(10, 100.0, targetSteps = 10))
    }
}
