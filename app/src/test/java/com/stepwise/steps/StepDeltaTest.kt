package com.stepwise.steps

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class StepDeltaTest {

    @Test
    fun firstReadingIsBaseline() {
        assertEquals(0L, StepDelta.nextDelta(null, 1234L))
    }

    @Test
    fun normalIncreaseReturnsDifference() {
        assertEquals(15L, StepDelta.nextDelta(1000L, 1015L))
    }

    @Test
    fun counterResetAfterRebootReturnsNewRawValue() {
        assertEquals(42L, StepDelta.nextDelta(90000L, 42L))
    }

    @Test
    fun equalValuesReturnZero() {
        assertEquals(0L, StepDelta.nextDelta(500L, 500L))
    }

    @Test
    fun dateChangesAcrossMidnightUtc() {
        // 2026-11-18T23:59:59Z and 2026-11-19T00:00:01Z
        val before = 1795046399000L
        val after = 1795046401000L
        assertEquals("2026-11-18", StepDelta.dateIsoFor(before, ZoneOffset.UTC))
        assertEquals("2026-11-19", StepDelta.dateIsoFor(after, ZoneOffset.UTC))
    }
}
