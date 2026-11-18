package com.cadence.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class DebugStatusTest {
    @Test
    fun reportsNoEventYetWhenNoneWasStored() {
        val lines = debugStatusLines(
            countingOn = true,
            sensorMissing = false,
            eventsReceived = 0L,
            lastEventMillis = 0L,
            lastRaw = 0L,
            nowMillis = 90_000L,
        )
        assertEquals(
            listOf(
                "Counting switched on: yes",
                "Step sensor missing flag: no",
                "Counter events received: 0",
                "Last counter event: none yet",
            ),
            lines,
        )
    }

    @Test
    fun reportsAgeOfLastEventAndItsRawValue() {
        val lines = debugStatusLines(
            countingOn = false,
            sensorMissing = true,
            eventsReceived = 42L,
            lastEventMillis = 1_000L,
            lastRaw = 1234L,
            nowMillis = 1_000L + 125_000L,
        )
        assertEquals("Last counter event: 2m 5s ago (raw 1234)", lines[3])
        assertEquals("Step sensor missing flag: yes", lines[1])
        assertEquals("Counter events received: 42", lines[2])
    }

    @Test
    fun formatAgeShowsSecondsUnderAMinute() {
        assertEquals("0s", formatAge(0L))
        assertEquals("59s", formatAge(59_999L))
        assertEquals("1m 0s", formatAge(60_000L))
    }

    @Test
    fun formatAgeTreatsNegativeAgeAsZero() {
        assertEquals("0s", formatAge(-5_000L))
    }
}
