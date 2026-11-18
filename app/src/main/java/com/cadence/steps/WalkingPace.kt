package com.cadence.steps

/** What the GPS speed says the user is doing. */
enum class PaceState {
    /** Walking or running at up to [WALKING_MAX_KMH]. */
    WALKING,

    /** Moving faster than walking, but not (yet) a sustained vehicle speed. */
    NOT_WALKING,

    /** A vehicle was detected by [VehicleDetector] and has not ended. Accelerometer steps are not counted. */
    VEHICLE,
}

/** GPS fixes less accurate than this are ignored by the walking-pace check, in metres. */
const val PACE_MAX_ACCURACY_M: Double = 20.0

/**
 * The highest speed still counted as walking, in km/h. It covers slow walking and running: a
 * runner at 8 to 15 km/h is walking for the step count.
 */
const val WALKING_MAX_KMH: Double = 15.0

/** Result of one accepted GPS fix: the pace state, and the vehicle event if the fix caused one. */
data class PaceUpdate(val state: PaceState, val vehicleEvent: VehicleEvent?)

/**
 * Classifies each accurate GPS fix as walking, not walking or vehicle. The vehicle decision is
 * made by [VehicleDetector], which needs the speed to stay at or above [VEHICLE_SPEED_KMH] for
 * [SUSTAINED_MS]. Pure Kotlin, no Android dependencies.
 */
class WalkingPace(private val vehicleDetector: VehicleDetector = VehicleDetector()) {

    private var inVehicle = false

    /**
     * Feeds one GPS fix. Returns null when the fix is ignored because its accuracy is worse than
     * [PACE_MAX_ACCURACY_M] or the speed is not a number. Otherwise returns the new [PaceUpdate].
     */
    fun onFix(speedKmh: Double, accuracyM: Double, timeMillis: Long): PaceUpdate? {
        if (accuracyM.isNaN() || accuracyM > PACE_MAX_ACCURACY_M) return null
        if (speedKmh.isNaN()) return null
        val kmh = speedKmh.coerceAtLeast(0.0)

        val event = vehicleDetector.onSpeed(kmh, timeMillis)
        when (event) {
            is VehicleStarted -> inVehicle = true
            VehicleEnded -> inVehicle = false
            null -> Unit
        }
        val state = when {
            inVehicle -> PaceState.VEHICLE
            kmh <= WALKING_MAX_KMH -> PaceState.WALKING
            else -> PaceState.NOT_WALKING
        }
        return PaceUpdate(state, event)
    }

    /** Forgets the vehicle state, for example when counting resumes. */
    fun reset() {
        vehicleDetector.reset()
        inVehicle = false
    }
}
