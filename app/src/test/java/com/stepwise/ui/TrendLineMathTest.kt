package com.stepwise.ui

import androidx.compose.ui.geometry.Offset
import com.stepwise.domain.DayPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendLineMathTest {

    private fun point(date: String, steps: Int, missing: Boolean = false) =
        DayPoint(date, steps, 0.0, missing)

    @Test
    fun weekdayAveragesSkipMissingDaysAndUseMondayFirst() {
        // 2026-11-16 is a Monday, 2026-11-17 Tuesday, 2026-11-18 Wednesday.
        val series = listOf(
            point("2026-11-16", 1000),
            point("2026-11-17", 0, missing = true),
            point("2026-11-18", 3000),
            point("2026-11-23", 5000),
        )
        val avgs = weekdayAverages(series)
        assertEquals(7, avgs.size)
        assertEquals(3000.0, avgs[0]!!, 0.0)     // Monday: 1000 and 5000
        assertNull(avgs[1])                      // Tuesday is missing
        assertEquals(3000.0, avgs[2]!!, 0.0)     // Wednesday
        assertNull(avgs[6])                      // Sunday has no data
    }

    @Test
    fun heatmapAlphaScalesFromFifteenPercentToFull() {
        assertEquals(0.15f, heatmapAlpha(null, 8000.0), 0f)
        assertEquals(0.15f, heatmapAlpha(0.0, 8000.0), 1e-6f)
        assertEquals(1f, heatmapAlpha(8000.0, 8000.0), 1e-6f)
        assertEquals(0.575f, heatmapAlpha(4000.0, 8000.0), 1e-6f)
        assertEquals(0.15f, heatmapAlpha(100.0, 0.0), 0f)
    }

    @Test
    fun movingAverageUsesTrailingWindowWithoutMissingDays() {
        val series = listOf(
            point("2026-11-01", 1000),
            point("2026-11-02", 0, missing = true),
            point("2026-11-03", 3000),
        )
        val avg = movingAverage(series, 2)
        assertEquals(1000.0, avg[0]!!, 0.0)
        assertEquals(1000.0, avg[1]!!, 0.0)
        assertEquals(3000.0, avg[2]!!, 0.0)
    }

    @Test
    fun movingAverageIsNullWhenWindowHasNoData() {
        val series = listOf(point("2026-11-01", 0, missing = true), point("2026-11-02", 500))
        assertNull(movingAverage(series, 1)[0])
    }

    @Test
    fun trimPolylineKeepsHalfOfALineAtHalfFraction() {
        val line = listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(10f, 10f))
        val half = trimPolyline(line, 0.5f)
        assertEquals(Offset(10f, 0f), half.last())
        assertEquals(2, half.size)
    }

    @Test
    fun trimPolylineEdgeCases() {
        val line = listOf(Offset(0f, 0f), Offset(10f, 0f))
        assertTrue(trimPolyline(line, 0f).isEmpty())
        assertEquals(line, trimPolyline(line, 1f))
        assertTrue(trimPolyline(listOf(Offset(1f, 1f)), 0.5f).isEmpty())
    }

    @Test
    fun tapIndexMapsPositionToColumn() {
        assertEquals(0, tapIndex(0f, 100f, 10))
        assertEquals(5, tapIndex(55f, 100f, 10))
        assertEquals(9, tapIndex(100f, 100f, 10))
        assertNull(tapIndex(10f, 100f, 0))
    }

    @Test
    fun compactStepsKeepsSeparatorsUnderTenThousand() {
        assertEquals("8,421", compactSteps(8421.4))
        assertEquals("12.3k", compactSteps(12345.0))
    }

    @Test
    fun dayLabelsUseFixedEnglishNames() {
        assertEquals("12 Mar", dayMonthLabel("2026-03-12"))
        assertEquals("Thu 12 Mar", dayLabelLong("2026-03-12"))
    }
}
