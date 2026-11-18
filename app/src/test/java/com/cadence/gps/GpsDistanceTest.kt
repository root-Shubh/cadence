package com.cadence.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsDistanceTest {

    private val base = 51.5000

    private fun fix(lat: Double, lon: Double = 0.0, acc: Float = 5f, t: Long) =
        GpsFix(lat, lon, acc, t)

    @Test
    fun haversineOverShortDistanceIsAboutOneHundredElevenMetres() {
        val a = fix(0.0, 0.0, t = 0)
        val b = fix(0.001, 0.0, t = 1000)
        val d = GpsDistance.haversineMeters(a, b)
        assertEquals(111.19, d, 111.19 * 0.01)
    }

    @Test
    fun accuracyFilterRejectsTwentyMetreFix() {
        val g = GpsDistance()
        g.addFix(fix(base, t = 0))
        val total = g.addFix(fix(base + 0.0001, acc = 20f, t = 5000))
        assertEquals(0.0, total, 0.0)
        assertEquals(1, g.acceptedFixCount)
    }

    @Test
    fun rejectedFixDoesNotBecomeLastGoodPoint() {
        val g = GpsDistance()
        g.addFix(fix(base, t = 0))
        g.addFix(fix(base + 0.001, acc = 40f, t = 2000))
        // Next good fix is measured from the first point, not the rejected one.
        g.addFix(fix(base + 0.0001, t = 4000))
        val expected = GpsDistance.haversineMeters(fix(base, t = 0), fix(base + 0.0001, t = 4000))
        assertEquals(expected, g.totalMeters, 1e-6)
    }

    @Test
    fun speedFilterRejectsFiveHundredMetreJumpInTenSeconds() {
        val g = GpsDistance()
        g.addFix(fix(base, t = 0))
        // ~500 m north in 10 s is ~50 m/s, far above 4 m/s.
        val total = g.addFix(fix(base + 500.0 / 111_195.0, t = 10_000))
        assertEquals(0.0, total, 0.0)
        assertEquals(1, g.acceptedFixCount)
    }

    @Test
    fun outOfOrderTimestampIsIgnored() {
        val g = GpsDistance()
        g.addFix(fix(base, t = 10_000))
        val total = g.addFix(fix(base + 0.0001, t = 9_000))
        assertEquals(0.0, total, 0.0)
        assertEquals(1, g.acceptedFixCount)
    }

    @Test
    fun equalTimestampIsIgnored() {
        val g = GpsDistance()
        g.addFix(fix(base, t = 1_000))
        g.addFix(fix(base + 0.00001, t = 1_000))
        assertEquals(1, g.acceptedFixCount)
    }

    @Test
    fun listFoldEqualsSequentialAdds() {
        val fixes = listOf(
            fix(base, t = 0),
            fix(base + 0.00005, t = 10_000),
            fix(base + 0.0001, acc = 30f, t = 12_000),
            fix(base + 0.0002, t = 20_000),
            fix(base + 0.0002, t = 19_000),
            fix(base + 0.0003, t = 30_000),
        )
        val sequential = GpsDistance()
        fixes.forEach { sequential.addFix(it) }
        assertEquals(sequential.totalMeters, distanceForFixes(fixes), 1e-9)
    }

    @Test
    fun emptyListIsZero() {
        assertEquals(0.0, distanceForFixes(emptyList()), 0.0)
    }

    @Test
    fun addFixReturnsUpdatedTotal() {
        val g = GpsDistance()
        assertEquals(0.0, g.addFix(fix(base, t = 0)), 0.0)
        val total = g.addFix(fix(base + 0.0001, t = 5_000))
        assertTrue(total > 0.0)
        assertEquals(g.totalMeters, total, 0.0)
    }
}
