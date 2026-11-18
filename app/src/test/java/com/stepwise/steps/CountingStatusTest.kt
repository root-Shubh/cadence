package com.stepwise.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class CountingStatusTest {

    private val now = 1_000_000_000L
    private val twoMin = 2 * 60 * 1000L

    @Test
    fun noPermissionWinsOverEverything() {
        val s = countingStatus(hasPermission = false, hasSensor = true, lastEventMillis = now, nowMillis = now)
        assertEquals(CountingStatus.NoPermission, s)
    }

    @Test
    fun noSensorWhenPermissionGranted() {
        val s = countingStatus(hasPermission = true, hasSensor = false, lastEventMillis = now, nowMillis = now)
        assertEquals(CountingStatus.NoSensor, s)
    }

    @Test
    fun waitingWhenNoEventYet() {
        val s = countingStatus(hasPermission = true, hasSensor = true, lastEventMillis = null, nowMillis = now)
        assertEquals(CountingStatus.WaitingForSteps, s)
    }

    @Test
    fun countingWhenEventWithinTwoMinutes() {
        val last = now - 30_000L
        val s = countingStatus(hasPermission = true, hasSensor = true, lastEventMillis = last, nowMillis = now)
        assertTrue(s is CountingStatus.Counting)
        assertEquals(last, (s as CountingStatus.Counting).lastEventMillis)
    }

    @Test
    fun countingAtExactlyTwoMinutes() {
        val s = countingStatus(hasPermission = true, hasSensor = true, lastEventMillis = now - twoMin, nowMillis = now)
        assertTrue(s is CountingStatus.Counting)
    }

    @Test
    fun idleWhenEventOlderThanTwoMinutes() {
        val last = now - twoMin - 1L
        val s = countingStatus(hasPermission = true, hasSensor = true, lastEventMillis = last, nowMillis = now)
        assertTrue(s is CountingStatus.Idle)
        assertEquals(last, (s as CountingStatus.Idle).lastEventMillis)
    }

    @Test
    fun describeMapsEachStateToPlainWords() {
        val utc = ZoneOffset.UTC
        assertEquals("Physical activity permission is off", describeCountingStatus(CountingStatus.NoPermission, utc))
        assertEquals("This phone has no step counter", describeCountingStatus(CountingStatus.NoSensor, utc))
        assertEquals("Waiting for your first step", describeCountingStatus(CountingStatus.WaitingForSteps, utc))
        assertEquals("Counting", describeCountingStatus(CountingStatus.Counting(now), utc))
        // 17:46 UTC
        val seventeenFortySix = java.time.LocalDateTime.of(2026, 11, 18, 17, 46).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals("Idle since 17:46", describeCountingStatus(CountingStatus.Idle(seventeenFortySix), utc))
    }
}
