package com.cadence.gps

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GpsFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
    val timeMillis: Long,
)

/**
 * Accumulates walking distance from GPS fixes.
 *
 * Rules:
 * - fixes with accuracy worse than [MAX_ACCURACY_M] are ignored;
 * - fixes whose time is not strictly after the last accepted fix are ignored;
 * - a segment whose implied speed exceeds [MAX_SPEED_MPS] is ignored.
 * Rejected fixes never become the last-good point.
 */
class GpsDistance {

    private var lastFix: GpsFix? = null

    var totalMeters: Double = 0.0
        private set

    var acceptedFixCount: Int = 0
        private set

    /** Adds a fix and returns the updated total distance in metres. */
    fun addFix(fix: GpsFix): Double {
        if (fix.accuracyM > MAX_ACCURACY_M) return totalMeters

        val previous = lastFix
        if (previous == null) {
            lastFix = fix
            acceptedFixCount++
            return totalMeters
        }

        if (fix.timeMillis <= previous.timeMillis) return totalMeters

        val distance = haversineMeters(previous, fix)
        val elapsedSeconds = (fix.timeMillis - previous.timeMillis) / 1000.0
        val speed = distance / elapsedSeconds
        if (speed > MAX_SPEED_MPS) return totalMeters

        totalMeters += distance
        lastFix = fix
        acceptedFixCount++
        return totalMeters
    }

    companion object {
        const val MAX_ACCURACY_M: Double = 15.0
        const val MAX_SPEED_MPS: Double = 4.0
        const val EARTH_RADIUS_M: Double = 6_371_000.0

        /** Great-circle distance in metres between two fixes (haversine). */
        fun haversineMeters(a: GpsFix, b: GpsFix): Double {
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val h = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }
    }
}

/** Folds the [GpsDistance] rules over [fixes] in order and returns total metres. */
fun distanceForFixes(fixes: List<GpsFix>): Double {
    val tracker = GpsDistance()
    fixes.forEach { tracker.addFix(it) }
    return tracker.totalMeters
}
