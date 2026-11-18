package com.cadence.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMathTest {

    private fun fix(lat: Double, lon: Double) = GpsFix(lat, lon, 5f, 0L)

    @Test
    fun distanceOfEmptyAndSinglePointIsZero() {
        assertEquals(0.0, routeDistanceMeters(emptyList()), 0.0)
        assertEquals(0.0, routeDistanceMeters(listOf(fix(51.0, 0.0))), 0.0)
    }

    @Test
    fun distanceOfStraightEastLineMatchesOneDegreeAtEquator() {
        // Along the equator one degree of longitude is about 111.195 km.
        val points = listOf(fix(0.0, 0.0), fix(0.0, 0.5), fix(0.0, 1.0))
        assertEquals(111_195.0, routeDistanceMeters(points), 50.0)
    }

    @Test
    fun distanceIsSumOfSegments() {
        val a = fix(0.0, 0.0)
        val b = fix(0.001, 0.0)
        val c = fix(0.002, 0.0)
        val total = routeDistanceMeters(listOf(a, b, c))
        assertEquals(GpsDistance.haversineMeters(a, b) + GpsDistance.haversineMeters(b, c), total, 1e-6)
    }

    @Test
    fun fitEmptyGivesEmpty() {
        assertTrue(fitRoute(emptyList(), 300f, 200f).isEmpty())
    }

    @Test
    fun fitOnePointGivesCentre() {
        val out = fitRoute(listOf(fix(51.5, -0.1)), 300f, 200f)
        assertEquals(1, out.size)
        assertEquals(150f, out[0].x, 1e-4f)
        assertEquals(100f, out[0].y, 1e-4f)
    }

    @Test
    fun fitPointsAtSameSpotGivesCentre() {
        val out = fitRoute(listOf(fix(51.5, -0.1), fix(51.5, -0.1)), 300f, 200f)
        assertTrue(out.all { it.x == 150f && it.y == 100f })
    }

    @Test
    fun fitStraightVerticalLineFillsHeightAndCentresX() {
        val points = listOf(fix(0.0, 0.0), fix(0.001, 0.0), fix(0.002, 0.0))
        val out = fitRoute(points, 300f, 200f)
        assertTrue(out.all { kotlin.math.abs(it.x - 150f) < 1e-3f })
        val ys = out.map { it.y }
        assertEquals(0f, ys.min(), 1e-3f)
        assertEquals(200f, ys.max(), 1e-3f)
        // Northernmost point (largest latitude) is drawn highest, i.e. smallest y.
        assertEquals(0f, out.last().y, 1e-3f)
    }

    @Test
    fun fitKeepsAspectRatio() {
        // Lon span 0.002, lat span 0.001 at the equator: a 2:1 box.
        val points = listOf(fix(0.0, 0.0), fix(0.001, 0.002))
        val out = fitRoute(points, 300f, 300f)
        val spanX = out.maxOf { it.x } - out.minOf { it.x }
        val spanY = out.maxOf { it.y } - out.minOf { it.y }
        assertEquals(2.0, (spanX / spanY).toDouble(), 1e-3)
        // Fits the box and is centred on both axes.
        assertEquals(300f, spanX, 1e-3f)
        assertEquals(150f, spanY, 1e-3f)
        assertEquals(75f, out.minOf { it.y }, 1e-3f)
        assertEquals(0f, out.minOf { it.x }, 1e-3f)
    }

    @Test
    fun fitRespectsPadding() {
        val points = listOf(fix(0.0, 0.0), fix(0.001, 0.002))
        val out = fitRoute(points, 300f, 300f, paddingPx = 10f)
        assertTrue(out.all { it.x in 10f..290f && it.y in 10f..290f })
    }

    @Test
    fun formatDistanceUsesMetresThenKilometres() {
        assertEquals("850 m", formatRouteDistance(849.6))
        assertEquals("1.24 km", formatRouteDistance(1_240.0))
    }

    @Test
    fun descriptionCoversEmptyAndRoute() {
        assertEquals("Today's route: no route yet", routeDescription(1, 0.0))
        assertEquals("Today's route: 1.24 km, 12 GPS points", routeDescription(12, 1_240.0))
    }
}
