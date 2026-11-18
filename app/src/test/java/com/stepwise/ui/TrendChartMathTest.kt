package com.stepwise.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TrendChartMathTest {

    @Test
    fun emptyListGivesEmptyList() {
        assertEquals(emptyList<Float>(), barFractions(emptyList()))
    }

    @Test
    fun allZeroGivesAllZeros() {
        assertEquals(listOf(0f, 0f, 0f), barFractions(listOf(0, 0, 0)))
    }

    @Test
    fun singleValueIsFullBar() {
        assertEquals(listOf(1f), barFractions(listOf(4200)))
    }

    @Test
    fun mixedValuesNormaliseToMax() {
        val result = barFractions(listOf(0, 2500, 5000, 10000))
        assertEquals(listOf(0f, 0.25f, 0.5f, 1f), result)
        assertEquals(1f, result.max(), 0f)
    }

    @Test
    fun negativeValuesAreClampedToZero() {
        assertEquals(listOf(0f, 1f), barFractions(listOf(-5, 80)))
    }
}
