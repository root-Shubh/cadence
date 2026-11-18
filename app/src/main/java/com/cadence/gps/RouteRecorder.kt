package com.cadence.gps

/** A kept fix with its smoothed position. The raw fix is kept too. */
data class RoutePoint(val raw: GpsFix, val smoothLat: Double, val smoothLon: Double)

/**
 * Decides which GPS fixes become route points. Pure Kotlin, no Android types.
 *
 * Rules, applied in order against the last kept point:
 * - accuracy must be at most [GpsDistance.MAX_ACCURACY_M];
 * - time must be strictly after the last kept point;
 * - implied speed must not exceed [GpsDistance.MAX_SPEED_MPS];
 * - at least [MIN_STEP_M] metres away from it.
 * The first fix is kept as long as it passes the accuracy rule. Rejected fixes never become
 * the last kept point and never reach the smoother. This class never affects step counting.
 */
class RouteRecorder(private val onKept: (RoutePoint) -> Unit) {

    private var lastKept: GpsFix? = null
    private val smoother = RouteSmoother()

    var keptCount: Int = 0
        private set

    /** Offers one fix; returns true when it was kept. */
    fun offer(fix: GpsFix): Boolean {
        if (fix.accuracyM > GpsDistance.MAX_ACCURACY_M) return false

        val previous = lastKept
        if (previous != null) {
            if (fix.timeMillis <= previous.timeMillis) return false
            val seconds = (fix.timeMillis - previous.timeMillis) / 1000.0
            val distance = GpsDistance.haversineMeters(previous, fix)
            if (distance / seconds > GpsDistance.MAX_SPEED_MPS) return false
            if (distance < MIN_STEP_M) return false
        }

        lastKept = fix
        keptCount++
        val (lat, lon) = smoother.update(fix)
        onKept(RoutePoint(fix, lat, lon))
        return true
    }

    /** Forgets the last kept point, the smoother and the count, for a new session. */
    fun reset() {
        lastKept = null
        keptCount = 0
        smoother.reset()
    }

    companion object {
        /** Minimum distance from the last kept point, in metres. */
        const val MIN_STEP_M: Double = 3.0
    }
}
