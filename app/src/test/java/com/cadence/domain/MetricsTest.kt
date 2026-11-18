package com.cadence.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MetricsTest {

    private val delta = 1e-6

    @Test
    fun maleFormulas() {
        // BMR = 10*80 + 6.25*180 - 5*30 + 5 = 1780; RMR = 1958
        // stride = 180*0.415 = 74.7 cm; distance = 10000*74.7/100 = 7470 m
        // active = 0.5*80*7.47 = 298.8; total = 1958 + 298.8 = 2256.8
        val m = computeDay(Profile(180.0, 80.0, 30, Sex.MALE), 10_000)
        assertEquals(1780.0, m.bmrKcal, delta)
        assertEquals(1958.0, m.rmrKcal, delta)
        assertEquals(74.7, strideCm(180.0, Sex.MALE), delta)
        assertEquals(7470.0, m.distanceM, delta)
        assertEquals(298.8, m.activeKcal, delta)
        assertEquals(2256.8, m.totalKcal, delta)
    }

    @Test
    fun femaleFormulas() {
        // BMR = 600 + 1031.25 - 200 - 161 = 1270.25; RMR = 1397.275
        // stride = 165*0.413 = 68.145; distance = 5000*68.145/100 = 3407.25 m
        // active = 0.5*60*3.40725 = 102.2175; total = 1499.4925
        val m = computeDay(Profile(165.0, 60.0, 40, Sex.FEMALE), 5_000)
        assertEquals(1270.25, m.bmrKcal, delta)
        assertEquals(1397.275, m.rmrKcal, delta)
        assertEquals(68.145, strideCm(165.0, Sex.FEMALE), delta)
        assertEquals(3407.25, m.distanceM, delta)
        assertEquals(102.2175, m.activeKcal, delta)
        assertEquals(1499.4925, m.totalKcal, delta)
    }

    @Test
    fun otherUsesAverageOfMaleAndFemale() {
        // BMR = 700 + 1062.5 - 125 + ((5 - 161)/2 = -78) = 1559.5; RMR = 1715.45
        // stride = 170*0.414 = 70.38; distance = 8000*70.38/100 = 5630.4 m
        // active = 0.5*70*5.6304 = 197.064; total = 1912.514
        val m = computeDay(Profile(170.0, 70.0, 25, Sex.OTHER), 8_000)
        assertEquals(1559.5, m.bmrKcal, delta)
        assertEquals(1715.45, m.rmrKcal, delta)
        assertEquals(70.38, strideCm(170.0, Sex.OTHER), delta)
        assertEquals(5630.4, m.distanceM, delta)
        assertEquals(197.064, m.activeKcal, delta)
        assertEquals(1912.514, m.totalKcal, delta)
    }

    @Test
    fun strideOverrideReplacesHeightEstimate() {
        // stride 80 cm; 1000 steps = 800 m; active = 0.5*80*0.8 = 32.0
        val m = computeDay(Profile(180.0, 80.0, 30, Sex.MALE, strideOverrideCm = 80.0), 1_000)
        assertEquals(800.0, m.distanceM, delta)
        assertEquals(32.0, m.activeKcal, delta)
    }

    @Test
    fun zeroStepsGivesZeroActiveAndRmrOnlyTotal() {
        val m = computeDay(Profile(180.0, 80.0, 30, Sex.MALE), 0)
        assertEquals(0.0, m.distanceM, delta)
        assertEquals(0.0, m.activeKcal, delta)
        assertEquals(m.rmrKcal, m.totalKcal, delta)
    }

    @Test
    fun invalidProfileThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            Profile(0.0, 80.0, 30, Sex.MALE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Profile(-1.0, 80.0, 30, Sex.MALE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Profile(180.0, 0.0, 30, Sex.MALE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Profile(180.0, 80.0, 0, Sex.MALE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Profile(180.0, 80.0, 30, Sex.MALE, strideOverrideCm = 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            computeDay(Profile(180.0, 80.0, 30, Sex.MALE), -1)
        }
    }
}
