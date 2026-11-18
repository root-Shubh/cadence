package com.stepwise.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleDetectorTest {

    /** Feeds one speed per second from [fromSec] to [toSec] inclusive; returns all events. */
    private fun feed(
        detector: VehicleDetector,
        kmh: Double,
        fromSec: Int,
        toSec: Int,
    ): List<Pair<Long, VehicleEvent>> {
        val events = mutableListOf<Pair<Long, VehicleEvent>>()
        for (s in fromSec..toSec) {
            val t = s * 1000L
            detector.onSpeed(kmh, t)?.let { events += t to it }
        }
        return events
    }

    @Test
    fun walkingSpeedNeverTriggers() {
        val detector = VehicleDetector()
        assertEquals(emptyList<Pair<Long, VehicleEvent>>(), feed(detector, 6.0, 0, 3_600))
        assertEquals(emptyList<Pair<Long, VehicleEvent>>(), feed(detector, 14.0, 3_601, 4_000))
    }

    @Test
    fun carSpeedTriggersAfterTwentySecondsNotBefore() {
        val detector = VehicleDetector()
        // Above the threshold from t=0. Nothing before 20 s.
        assertEquals(emptyList<Pair<Long, VehicleEvent>>(), feed(detector, 50.0, 0, 19))
        val events = feed(detector, 50.0, 20, 20)
        assertEquals(listOf(20_000L to VehicleStarted(0L)), events)
    }

    @Test
    fun startTimeIsWhenSpeedFirstWentAboveThreshold() {
        val detector = VehicleDetector()
        feed(detector, 5.0, 0, 9)
        val events = feed(detector, 40.0, 10, 40)
        assertEquals(VehicleStarted(10_000L), events.single().second)
    }

    @Test
    fun briefSpikeDoesNotTrigger() {
        val detector = VehicleDetector()
        assertEquals(emptyList<Pair<Long, VehicleEvent>>(), feed(detector, 60.0, 0, 5))
        assertEquals(emptyList<Pair<Long, VehicleEvent>>(), feed(detector, 4.0, 6, 100))
    }

    @Test
    fun speedDroppingBackEndsVehicle() {
        val detector = VehicleDetector()
        feed(detector, 50.0, 0, 20)
        // Slow for less than 20 s: still in a vehicle.
        assertEquals(emptyList<Pair<Long, VehicleEvent>>(), feed(detector, 3.0, 21, 39))
        // Slow for 20 s: ends.
        assertEquals(listOf(41_000L to VehicleEnded), feed(detector, 3.0, 40, 41))
    }

    @Test
    fun briefSlowdownInVehicleDoesNotEnd() {
        val detector = VehicleDetector()
        feed(detector, 50.0, 0, 20)
        feed(detector, 2.0, 21, 30)
        assertEquals(emptyList<Pair<Long, VehicleEvent>>(), feed(detector, 50.0, 31, 100))
    }

    @Test
    fun resetForgetsVehicleState() {
        val detector = VehicleDetector()
        feed(detector, 50.0, 0, 20)
        detector.reset()
        assertNull(detector.onSpeed(3.0, 30_000L))
        assertEquals(listOf(51_000L to VehicleStarted(31_000L)), feed(detector, 50.0, 31, 60))
    }

    @Test
    fun stepsToRemoveUsesTotalAtOrBeforeStart() {
        val log = listOf(
            1_000L to 100,
            5_000L to 150,
            9_000L to 180,
            12_000L to 200,
        )
        // Start at 10 s: baseline is the entry at 9 s (180). Current 260 gives 80 to remove.
        assertEquals(80, stepsToRemove(log, 10_000L, 260))
        // Start exactly on an entry: that entry is the baseline.
        assertEquals(60, stepsToRemove(log, 5_000L, 210))
    }

    @Test
    fun stepsToRemoveIsZeroWithoutBaseline() {
        assertEquals(0, stepsToRemove(listOf(5_000L to 50), 1_000L, 80))
        assertEquals(0, stepsToRemove(emptyList(), 1_000L, 80))
    }

    @Test
    fun stepsToRemoveNeverNegativeOrAboveCurrent() {
        // Baseline above the current total (for example after a manual reset): nothing removed.
        assertEquals(0, stepsToRemove(listOf(1_000L to 500), 2_000L, 300))
        // Current total below zero is treated as zero.
        assertEquals(0, stepsToRemove(listOf(1_000L to 10), 2_000L, -5))
    }

    @Test
    fun appendKeepsOnlyEntriesFromWindowStart() {
        val log = listOf(100L to 1, 500L to 2)
        assertEquals(listOf(500L to 2, 900L to 3), appendToVehicleLog(log, 900L, 3, 400L))
    }

    @Test
    fun logRoundTripsAndSkipsBadEntries() {
        val log = listOf(1_000L to 10, 2_000L to 20)
        assertEquals(log, decodeVehicleLog(encodeVehicleLog(log)))
        assertEquals(listOf(1_000L to 10), decodeVehicleLog("1000:10;bad;2000:x"))
        assertEquals(emptyList<Pair<Long, Int>>(), decodeVehicleLog(null))
    }
}
