package com.cadence.gps

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * One-dimensional Kalman filter applied to each axis of the accepted route, in local metres.
 * Both axes share the same gain because they share the same noise model, so the smoothed path
 * keeps the direction of travel. The measurement noise is the fix's accuracy. Pure Kotlin.
 *
 * [processNoise] is the expected wander of the walker in m^2 per second. Smaller values give a
 * straighter, more lagged line.
 */
class RouteSmoother(private val processNoise: Double = 2.0) {

    private var originLat = 0.0
    private var originLon = 0.0
    private var cosLat = 1.0
    private var x = 0.0
    private var y = 0.0
    private var variance = 0.0
    private var lastTimeMillis = 0L
    private var started = false

    fun reset() {
        started = false
    }

    /** Feeds one accepted fix and returns its smoothed latitude and longitude. */
    fun update(fix: GpsFix): Pair<Double, Double> {
        if (!started) {
            originLat = fix.latitude
            originLon = fix.longitude
            cosLat = cos(Math.toRadians(originLat))
        }
        val mx = (fix.longitude - originLon) * cosLat * METRES_PER_DEG
        val my = (fix.latitude - originLat) * METRES_PER_DEG
        val r = max(fix.accuracyM.toDouble(), 1.0).let { it * it }
        if (!started) {
            x = mx
            y = my
            variance = r
            started = true
        } else {
            val dt = max((fix.timeMillis - lastTimeMillis) / 1000.0, 0.001)
            val predicted = variance + processNoise * dt
            val gain = predicted / (predicted + r)
            x += gain * (mx - x)
            y += gain * (my - y)
            variance = (1 - gain) * predicted
        }
        lastTimeMillis = fix.timeMillis
        val lat = originLat + y / METRES_PER_DEG
        val lon = originLon + x / (METRES_PER_DEG * cosLat)
        return lat to lon
    }

    companion object {
        /** Metres per degree of arc along a meridian. */
        val METRES_PER_DEG: Double = GpsDistance.EARTH_RADIUS_M * PI / 180.0
    }
}
