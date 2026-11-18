package com.cadence.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisionalLeadTest {

    @Test
    fun accelerometerStepsShowDuringWarmUpBeforeAnyDetectorStep() {
        val lead = ProvisionalLead()
        for (t in 0L until 6_000L step 600L) assertTrue(lead.onAccelStep(t))
        assertEquals(10L, lead.current(6_000L))
        assertFalse(lead.isSensorActive())
    }

    @Test
    fun onceTheSensorIsActiveAccelerometerStepsAreIgnored() {
        val lead = ProvisionalLead()
        lead.onAccelStep(0L)
        lead.onDetectorStep(600L)
        assertTrue(lead.isSensorActive())
        // Walking on: accelerometer steps keep coming every 0.6 s, the detector too.
        assertFalse(lead.onAccelStep(1_200L))
        lead.onDetectorStep(1_200L)
        assertFalse(lead.onAccelStep(1_800L))
        assertEquals(3L, lead.current(1_800L)) // one warm-up step plus two detector steps
    }

    @Test
    fun handlingThePhoneRightAfterAWalkIsIgnored() {
        // The pocket test: a walk with both sensors reporting, ending at 10 s, then the phone is
        // taken out and unlocked with no pause.
        val lead = ProvisionalLead()
        for (t in 0L..10_000L step 500L) {
            lead.onAccelStep(t)
            if (t >= 1_000L) lead.onDetectorStep(t)
        }
        lead.takeForCounter(10_000L)
        var counted = 0
        for (t in 10_500L until 10_000L + SENSOR_SILENT_MS step 500L) if (lead.onAccelStep(t)) counted++
        assertEquals(0, counted)
    }

    @Test
    fun accelerometerLeadStopsAtTheWarmUpCap() {
        val lead = ProvisionalLead(maxAccelLead = WARMUP_LEAD_CAP)
        var counted = 0
        for (t in 0L until 30L * 600L step 600L) if (lead.onAccelStep(t)) counted++
        assertEquals(WARMUP_LEAD_CAP.toInt(), counted)
        assertEquals(WARMUP_LEAD_CAP, lead.current(30L * 600L))
        // The counter confirms: a new warm-up may show again.
        lead.takeForCounter(18_000L)
        assertTrue(lead.onAccelStep(18_600L))
    }

    @Test
    fun aBreakInMovementLetsTheAccelerometerShowTheNextWalk() {
        val lead = ProvisionalLead()
        lead.onAccelStep(0L)
        lead.onDetectorStep(600L)
        lead.takeForCounter(600L)
        assertFalse(lead.onAccelStep(1_000L))
        // Standing still for 3 s, then walking again: shown from the first step.
        assertTrue(lead.onAccelStep(1_000L + MOVEMENT_BREAK_MS))
        assertFalse(lead.isSensorActive())
        assertTrue(lead.onAccelStep(1_600L + MOVEMENT_BREAK_MS))
        assertEquals(2L, lead.current(1_600L + MOVEMENT_BREAK_MS))
    }

    @Test
    fun aSilentSensorWhileMovingLetsTheAccelerometerShowStepsAgain() {
        val lead = ProvisionalLead()
        lead.onAccelStep(0L)
        lead.onDetectorStep(100L)
        lead.takeForCounter(100L)
        // Moving steadily; the detector stops reporting after its step at 0.1 s.
        var t = 500L
        while (t < SENSOR_SILENT_MS) {
            assertFalse(lead.onAccelStep(t))
            t += 500L
        }
        assertTrue(lead.onAccelStep(100L + SENSOR_SILENT_MS))
    }

    @Test
    fun counterReadingTakesTheLeadAndStartsANewOne() {
        val lead = ProvisionalLead()
        repeat(12) { lead.onAccelStep(it * 600L) }
        assertEquals(12L, lead.takeForCounter(7_000L))
        assertEquals(0L, lead.current(7_000L))
    }

    @Test
    fun unconfirmedLeadExpires() {
        val lead = ProvisionalLead()
        repeat(5) { lead.onAccelStep(it * 600L) }
        assertEquals(5L, lead.current(2_400L + LEAD_EXPIRY_MS))
        assertEquals(0L, lead.current(2_400L + LEAD_EXPIRY_MS + 1L))
        assertEquals(0L, lead.takeForCounter(2_400L + LEAD_EXPIRY_MS + 1L))
    }

    @Test
    fun noExpiryWhenDisabled() {
        val lead = ProvisionalLead(expiryMs = Long.MAX_VALUE)
        lead.onAccelStep(0L)
        assertEquals(1L, lead.current(10L * 60L * 1000L))
    }

    @Test
    fun resetForgetsTheLeadAndTheSensorState() {
        val lead = ProvisionalLead()
        lead.onDetectorStep(1_000L)
        lead.reset()
        assertEquals(0L, lead.current(1_000L))
        assertFalse(lead.isSensorActive())
        assertTrue(lead.onAccelStep(1_100L))
    }
}
