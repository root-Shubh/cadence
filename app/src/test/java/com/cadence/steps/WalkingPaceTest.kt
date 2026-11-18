package com.cadence.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalkingPaceTest {

    @Test
    fun fixWorseThanTwentyMetresIsIgnored() {
        val pace = WalkingPace()
        assertNull(pace.onFix(5.0, 25.0, 1_000L))
        assertNull(pace.onFix(Double.NaN, 5.0, 1_000L))
    }

    @Test
    fun slowWalkIsWalking() {
        val update = WalkingPace().onFix(5.0, 5.0, 1_000L)
        assertEquals(PaceState.WALKING, update?.state)
        assertNull(update?.vehicleEvent)
    }

    @Test
    fun runningUpToFifteenKmhCountsAsWalking() {
        assertEquals(PaceState.WALKING, WalkingPace().onFix(12.0, 5.0, 1_000L)?.state)
    }

    @Test
    fun speedBetweenRunningAndVehicleIsNotWalking() {
        assertEquals(PaceState.NOT_WALKING, WalkingPace().onFix(17.0, 5.0, 1_000L)?.state)
    }

    @Test
    fun sustainedCarSpeedIsVehicleAndEndsOnSlowdown() {
        val pace = WalkingPace()
        var started: VehicleEvent? = null
        for (s in 0..20) {
            pace.onFix(50.0, 5.0, s * 1000L)?.vehicleEvent?.let { started = it }
        }
        assertEquals(VehicleStarted(0L), started)
        assertEquals(PaceState.VEHICLE, pace.onFix(50.0, 5.0, 21_000L)?.state)

        var ended: VehicleEvent? = null
        for (s in 22..50) {
            pace.onFix(3.0, 5.0, s * 1000L)?.vehicleEvent?.let { ended = it }
        }
        assertEquals(VehicleEnded, ended)
        assertEquals(PaceState.WALKING, pace.onFix(3.0, 5.0, 51_000L)?.state)
    }

    @Test
    fun briefFastSpikeDoesNotMakeVehicle() {
        val pace = WalkingPace()
        pace.onFix(60.0, 5.0, 0L)
        pace.onFix(60.0, 5.0, 3_000L)
        assertEquals(PaceState.NOT_WALKING, pace.onFix(60.0, 5.0, 4_000L)?.state)
        assertEquals(PaceState.WALKING, pace.onFix(4.0, 5.0, 5_000L)?.state)
    }
}
