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

class ChartMathLabelTest {

    @Test
    fun todayColumnIsLabelledToday() {
        assertEquals("Today", weekdayLabel("2026-11-18", "2026-11-18"))
    }

    @Test
    fun otherDaysUseShortEnglishNames() {
        // 2026-11-16 is a Monday, 2026-11-22 a Sunday.
        assertEquals("Mon", weekdayLabel("2026-11-16", "2026-11-18"))
        assertEquals("Wed", weekdayLabel("2026-11-18", "2026-11-17"))
        assertEquals("Sun", weekdayLabel("2026-11-22", "2026-11-18"))
    }

    @Test
    fun thirtyItemsStepFiveIncludesLastDay() {
        assertEquals(listOf(0, 5, 10, 15, 20, 25, 29), axisLabelIndices(30, 5))
    }

    @Test
    fun lastIndexNotDuplicatedWhenOnStep() {
        assertEquals(listOf(0, 5, 10), axisLabelIndices(11, 5))
    }

    @Test
    fun emptyCountGivesNoIndices() {
        assertEquals(emptyList<Int>(), axisLabelIndices(0, 5))
    }

    @Test
    fun singleItemGivesZeroOnly() {
        assertEquals(listOf(0), axisLabelIndices(1, 5))
    }
}
