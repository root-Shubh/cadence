package com.cadence.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetThrottleTest {

    @Test
    fun firstRequestIsSentAtOnce() {
        assertTrue(WidgetThrottle(5_000L).request(0L))
    }

    @Test
    fun requestsInsideTheIntervalWaitButAreNotLost() {
        val throttle = WidgetThrottle(5_000L)
        throttle.request(0L)
        assertFalse(throttle.request(1_000L))
        assertTrue(throttle.hasPending())
        // The walk stops: no new request comes, but the kept one is sent once due.
        assertFalse(throttle.takePendingIfDue(4_999L))
        assertTrue(throttle.takePendingIfDue(5_000L))
        assertFalse(throttle.hasPending())
        assertFalse(throttle.takePendingIfDue(20_000L))
    }

    @Test
    fun requestAfterTheIntervalIsSentAtOnce() {
        val throttle = WidgetThrottle(5_000L)
        throttle.request(0L)
        assertTrue(throttle.request(5_000L))
    }

    @Test
    fun clockGoingBackwardsDoesNotBlockRedraws() {
        val throttle = WidgetThrottle(5_000L)
        throttle.request(10_000L)
        assertTrue(throttle.request(1_000L))
    }
}
