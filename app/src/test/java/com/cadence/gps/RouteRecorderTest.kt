package com.cadence.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRecorderTest {

    private val kept = mutableListOf<GpsFix>()
    private val recorder = RouteRecorder { kept.add(it) }

    /** About 1.11 m per 0.00001 degree of latitude. */
    private fun fix(latStep: Int, timeMillis: Long, accuracy: Float = 5f) =
        GpsFix(latitude = 51.0 + latStep * 0.00001, longitude = 0.0, accuracyM = accuracy, timeMillis = timeMillis)

    @Test
    fun firstFixWithGoodAccuracyIsKept() {
        assertTrue(recorder.offer(fix(0, 1_000L)))
        assertEquals(1, kept.size)
        assertEquals(1, recorder.keptCount)
    }

    @Test
    fun fixWorseThanAccuracyLimitIsRejected() {
        val limit = GpsDistance.MAX_ACCURACY_M.toFloat()
        assertFalse(recorder.offer(fix(0, 1_000L, accuracy = limit + 1f)))
        assertTrue(recorder.offer(fix(0, 2_000L, accuracy = limit)))
        assertEquals(1, kept.size)
    }

    @Test
    fun pointLessThanFiveMetresFromLastKeptIsRejected() {
        recorder.offer(fix(0, 1_000L))
        // 3 m north of the first point: too close.
        assertFalse(recorder.offer(fix(3, 3_000L)))
        // 6 m north: kept.
        assertTrue(recorder.offer(fix(6, 4_000L)))
        assertEquals(2, kept.size)
    }

    @Test
    fun rejectedPointDoesNotBecomeTheReference() {
        recorder.offer(fix(0, 1_000L))
        recorder.offer(fix(3, 2_000L))
        // 6 m from the first kept point; if the rejected 3 m point had become the reference this
        // would only be 3 m away and be refused.
        assertTrue(recorder.offer(fix(6, 3_000L)))
    }

    @Test
    fun speedAboveLimitIsRejected() {
        recorder.offer(fix(0, 0L))
        // About 11 m in 1 s is about 11 m/s, well above 4 m/s.
        assertFalse(recorder.offer(fix(10, 1_000L)))
        // Same distance after 5 s is about 2.2 m/s: kept.
        assertTrue(recorder.offer(fix(10, 5_000L)))
    }

    @Test
    fun fixNotAfterLastKeptTimeIsRejected() {
        recorder.offer(fix(0, 5_000L))
        assertFalse(recorder.offer(fix(20, 5_000L)))
        assertFalse(recorder.offer(fix(20, 4_000L)))
    }

    @Test
    fun resetForgetsReference() {
        recorder.offer(fix(0, 1_000L))
        recorder.reset()
        assertEquals(0, recorder.keptCount)
        assertTrue(recorder.offer(fix(1, 1_000L)))
    }
}
