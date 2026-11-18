package com.cadence.gps

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSmootherTest {

    private val originLat = 51.5
    private val originLon = -0.12
    private val metresPerDeg = RouteSmoother.METRES_PER_DEG

    /** Converts local metres east/north of the origin to a fix. */
    private fun fixAt(eastM: Double, northM: Double, timeMillis: Long, accuracy: Float): GpsFix {
        val cosLat = cos(Math.toRadians(originLat))
        return GpsFix(
            latitude = originLat + northM / metresPerDeg,
            longitude = originLon + eastM / (metresPerDeg * cosLat),
            accuracyM = accuracy,
            timeMillis = timeMillis,
        )
    }

    @Test
    fun noisyStraightWalkStaysWithinThreeMetresOfTheLine() {
        // A straight walk north-east, one fix every 2 s. The noise is at most 2 m and is applied
        // perpendicular to the line, so the raw fixes are never more than 2 m off it.
        val dirE = 1.0 / hypot(1.0, 1.0)
        val dirN = dirE
        val smoother = RouteSmoother()
        var maxDeviation = 0.0
        for (i in 0 until 120) {
            val along = i * 2.4
            val noise = 2.0 * sin(i * 1.3)
            val fix = fixAt(
                eastM = along * dirE - dirN * noise,
                northM = along * dirN + dirE * noise,
                timeMillis = i * 2_000L,
                accuracy = 5f,
            )
            val (lat, lon) = smoother.update(fix)
            val cosLat = cos(Math.toRadians(originLat))
            val e = (lon - originLon) * metresPerDeg * cosLat
            val n = (lat - originLat) * metresPerDeg
            // Distance from the true line through the origin with direction (dirE, dirN).
            maxDeviation = maxOf(maxDeviation, abs(e * dirN - n * dirE))
        }
        assertTrue("max deviation $maxDeviation m", maxDeviation <= 3.0)
    }

    @Test
    fun firstPointIsItsOwnSmoothedPosition() {
        val smoother = RouteSmoother()
        val fix = fixAt(10.0, 20.0, 0L, 5f)
        val (lat, lon) = smoother.update(fix)
        assertTrue(abs(lat - fix.latitude) < 1e-9)
        assertTrue(abs(lon - fix.longitude) < 1e-9)
    }

    @Test
    fun resetStartsAgainFromTheNextFix() {
        val smoother = RouteSmoother()
        smoother.update(fixAt(0.0, 0.0, 0L, 5f))
        smoother.reset()
        val far = fixAt(500.0, 500.0, 10_000L, 5f)
        val (lat, lon) = smoother.update(far)
        assertTrue(abs(lat - far.latitude) < 1e-9)
        assertTrue(abs(lon - far.longitude) < 1e-9)
    }
}
