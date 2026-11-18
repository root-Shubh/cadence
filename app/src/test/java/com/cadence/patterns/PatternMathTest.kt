package com.cadence.patterns

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternMathTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private fun millisAt(date: String, hour: Int, minute: Int, second: Int = 0): Long =
        ZonedDateTime.of(
            java.time.LocalDate.parse(date).atTime(hour, minute, second),
            zone,
        ).toInstant().toEpochMilli()

    @Test
    fun everyBucketWidthAddsUpToTheStepCount() {
        val steps = listOf(
            millisAt("2026-11-18", 0, 0, 1),
            millisAt("2026-11-18", 7, 30),
            millisAt("2026-11-18", 7, 31),
            millisAt("2026-11-18", 18, 45, 59),
            millisAt("2026-11-18", 23, 59, 59),
        )
        for (width in BUCKET_MINUTES) {
            val buckets = bucketCounts(steps, "2026-11-18", zone, width)
            assertEquals("width $width", steps.size.toLong(), buckets.sum())
        }
    }

    @Test
    fun hourlyBucketsAreLocalHours() {
        val steps = listOf(
            millisAt("2026-11-18", 7, 0),
            millisAt("2026-11-18", 7, 59),
            millisAt("2026-11-18", 18, 0),
        )
        val hourly = bucketCounts(steps, "2026-11-18", zone, 60)
        assertEquals(2L, hourly[7])
        assertEquals(1L, hourly[18])
        assertEquals(2, activeHours(hourly))
        assertEquals(7, peakIndex(hourly))
    }

    @Test
    fun dayRollsOverAtLocalMidnight() {
        val steps = listOf(
            millisAt("2026-11-18", 23, 59, 59),
            millisAt("2026-11-19", 0, 0, 0),
            millisAt("2026-11-19", 0, 0, 1),
        )
        val day18 = bucketCounts(steps, "2026-11-18", zone, 60)
        val day19 = bucketCounts(steps, "2026-11-19", zone, 60)
        assertEquals(1L, day18.sum())
        assertEquals(1L, day18[23])
        assertEquals(2L, day19.sum())
        assertEquals(2L, day19[0])
        assertEquals("2026-11-18", dayIsoOf(millisAt("2026-11-18", 23, 59, 59), zone))
        assertEquals("2026-11-19", dayIsoOf(millisAt("2026-11-19", 0, 0, 0), zone))
    }

    @Test
    fun emptyDayHasNoPeakAndNoActiveHours() {
        val hourly = bucketCounts(emptyList(), "2026-11-18", zone, 60)
        assertEquals(-1, peakIndex(hourly))
        assertEquals(0, activeHours(hourly))
    }

    @Test
    fun zoomMapsToFinerBucketsAndClamps() {
        assertEquals(60, bucketMinutesForZoom(1.0))
        assertEquals(30, bucketMinutesForZoom(2.0))
        assertEquals(15, bucketMinutesForZoom(4.0))
        assertEquals(5, bucketMinutesForZoom(12.0))
        assertEquals(1, bucketMinutesForZoom(60.0))
        assertEquals(60, bucketMinutesForZoom(0.1))
        assertEquals(1, bucketMinutesForZoom(1_000.0))
        assertTrue(BUCKET_MINUTES.all { 60 % it == 0 })
    }
}
