package com.cadence.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrendTest {

    private val end = LocalDate.of(2026, 11, 18)
    private val delta = 1e-6

    @Test
    fun sevenDayWindowWithMissingDays() {
        // current window 2026-11-12..2026-11-18; previous 2026-11-05..2026-11-11
        val history = listOf(
            DailyRecord("2026-11-12", 1000, 2000.0),
            DailyRecord("2026-11-14", 3000, 2200.0),
            DailyRecord("2026-11-18", 5000, 2400.0),
            DailyRecord("2026-11-05", 2000, 1900.0),
        )
        val t = computeTrend(history, end, 7)

        assertEquals(7, t.series.size)
        assertEquals("2026-11-12", t.series.first().dateIso)
        assertEquals("2026-11-18", t.series.last().dateIso)
        assertEquals(4, t.series.count { it.missing })
        val day13 = t.series.first { it.dateIso == "2026-11-13" }
        assertTrue(day13.missing)
        assertEquals(0, day13.steps)
        assertEquals(0.0, day13.totalKcal, delta)
        assertFalse(t.series.first { it.dateIso == "2026-11-14" }.missing)

        // averages over days with data only: steps (1000+3000+5000)/3, kcal (2000+2200+2400)/3
        assertEquals(3000.0, t.avgSteps!!, delta)
        assertEquals(2200.0, t.avgKcal!!, delta)
        assertEquals(9000L, t.totalSteps)
        // previous avg = 2000; change = (3000-2000)/2000*100 = 50
        assertEquals(50.0, t.percentChangeSteps!!, delta)
    }

    @Test
    fun emptyHistoryDoesNotCrash() {
        val t = computeTrend(emptyList(), end, 7)
        assertEquals(7, t.series.size)
        assertTrue(t.series.all { it.missing && it.steps == 0 })
        assertNull(t.avgSteps)
        assertNull(t.avgKcal)
        assertEquals(0L, t.totalSteps)
        assertNull(t.percentChangeSteps)
    }

    @Test
    fun previousWindowWithNoDataGivesNullPercentChange() {
        val history = listOf(DailyRecord("2026-11-18", 4000, 2100.0))
        val t = computeTrend(history, end, 7)
        assertEquals(4000.0, t.avgSteps!!, delta)
        assertNull(t.percentChangeSteps)
    }

    @Test
    fun thirtyDayWindow() {
        // current window 2026-10-20..2026-11-18; previous 2026-09-20..2026-10-19
        val history = listOf(
            DailyRecord("2026-10-19", 1000, 1800.0), // outside current window, feeds previous
            DailyRecord("2026-10-20", 1000, 1800.0),
            DailyRecord("2026-11-18", 3000, 2300.0),
        )
        val t = computeTrend(history, end, 30)
        assertEquals(30, t.series.size)
        assertEquals("2026-10-20", t.series.first().dateIso)
        assertEquals("2026-11-18", t.series.last().dateIso)
        assertEquals(28, t.series.count { it.missing })
        assertEquals(2000.0, t.avgSteps!!, delta)
        assertEquals(2050.0, t.avgKcal!!, delta)
        assertEquals(4000L, t.totalSteps)
        // previous avg = 1000; change = (2000-1000)/1000*100 = 100
        assertEquals(100.0, t.percentChangeSteps!!, delta)
    }

    @Test
    fun nonPositiveWindowThrows() {
        try {
            computeTrend(emptyList(), end, 0)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
