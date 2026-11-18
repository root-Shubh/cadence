package com.stepwise.steps

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTotalTest {

    @Test
    fun counterAheadWins() {
        assertEquals(1200, liveTotal(counterTotal = 1200, accelTotal = 1100))
    }

    @Test
    fun accelAheadWins() {
        assertEquals(1300, liveTotal(counterTotal = 1200, accelTotal = 1300))
    }

    @Test
    fun equalSourcesGiveTheSameValue() {
        assertEquals(500, liveTotal(counterTotal = 500, accelTotal = 500))
    }

    @Test
    fun storedNeverGoesBackwards() {
        assertEquals(900, mergeStored(previousStored = 900, candidate = 800))
    }

    @Test
    fun storedMovesForwardToCandidate() {
        assertEquals(950, mergeStored(previousStored = 900, candidate = 950))
    }

    @Test
    fun storedKeepsPreviousWhenEqual() {
        assertEquals(900, mergeStored(previousStored = 900, candidate = 900))
    }
}
