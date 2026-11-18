package com.cadence.steps

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class StepDeltaTest {

    @Test
    fun firstCounterReadingIsBaselineAndCreditsOnlyTheDetectorLead() {
        // Raw 5000 includes steps walked while counting was off: none of them is added.
        assertEquals(0L, StepDelta.counterDelta(lastRaw = null, currentRaw = 5000L, lead = 0L))
        // Six detector steps were seen before the counter's first reading: they are inside it.
        assertEquals(6L, StepDelta.counterDelta(lastRaw = null, currentRaw = 5006L, lead = 6L))
    }

    @Test
    fun laterCounterReadingsAddTheDifference() {
        assertEquals(11L, StepDelta.counterDelta(lastRaw = 5006L, currentRaw = 5017L, lead = 3L))
    }

    @Test
    fun counterResetAfterRebootAddsStepsSinceReboot() {
        assertEquals(40L, StepDelta.counterDelta(lastRaw = 5017L, currentRaw = 40L, lead = 0L))
    }

    @Test
    fun negativeLeadIsTreatedAsZero() {
        assertEquals(0L, StepDelta.counterDelta(lastRaw = null, currentRaw = 10L, lead = -2L))
    }

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
