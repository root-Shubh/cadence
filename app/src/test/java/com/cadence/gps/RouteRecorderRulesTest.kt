package com.cadence.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRecorderRulesTest {

    private val kept = mutableListOf<RoutePoint>()
    private val recorder = RouteRecorder { kept.add(it) }

    /** About 1.11 m per 0.00001 degree of latitude. */
    private fun fix(latStep: Int, timeMillis: Long, accuracy: Float = 5f) =
        GpsFix(latitude = 51.0 + latStep * 0.00001, longitude = 0.0, accuracyM = accuracy, timeMillis = timeMillis)

    @Test
    fun firstFixWithGoodAccuracyIsKeptWithASmoothedPosition() {
        assertTrue(recorder.offer(fix(0, 1_000L)))
        assertEquals(1, kept.size)
        assertEquals(51.0, kept[0].smoothLat, 1e-9)
    }

    @Test
    fun accuracyAboveLimitIsRejectedAndLimitItselfIsKept() {
        val limit = GpsDistance.MAX_ACCURACY_M.toFloat()
        assertFalse(recorder.offer(fix(0, 1_000L, accuracy = limit + 1f)))
        assertTrue(recorder.offer(fix(0, 2_000L, accuracy = limit)))
        assertEquals(1, kept.size)
    }

    @Test
    fun timeMustBeStrictlyAfterLastKept() {
        recorder.offer(fix(0, 5_000L))
        assertFalse(recorder.offer(fix(20, 5_000L)))
        assertFalse(recorder.offer(fix(20, 4_000L)))
        // 22 m in 10 s is 2.2 m/s: kept. (In 4 s it would be 5.5 m/s, which is refused.)
        assertTrue(recorder.offer(fix(20, 15_000L)))
    }

    @Test
    fun speedAboveFourMetresPerSecondIsRejected() {
        recorder.offer(fix(0, 0L))
        // About 11 m in 1 s is about 11 m/s.
        assertFalse(recorder.offer(fix(10, 1_000L)))
        // The same distance after 5 s is about 2.2 m/s.
        assertTrue(recorder.offer(fix(10, 5_000L)))
    }

    @Test
    fun pointLessThanThreeMetresFromLastKeptIsRejected() {
        recorder.offer(fix(0, 1_000L))
        // About 2.2 m north: too close.
        assertFalse(recorder.offer(fix(2, 3_000L)))
        // About 4.4 m north: kept.
        assertTrue(recorder.offer(fix(4, 5_000L)))
        assertEquals(2, kept.size)
    }

    @Test
    fun rejectedPointDoesNotBecomeTheReference() {
        recorder.offer(fix(0, 1_000L))
        recorder.offer(fix(2, 2_000L))
        // 4.4 m from the first kept point. If the rejected 2.2 m point had become the reference,
        // this would be only 2.2 m away and be refused.
        assertTrue(recorder.offer(fix(4, 3_000L)))
    }

    @Test
    fun resetForgetsReferenceAndCount() {
        recorder.offer(fix(0, 1_000L))
        recorder.reset()
        assertEquals(0, recorder.keptCount)
        assertTrue(recorder.offer(fix(1, 1_000L)))
    }
}
