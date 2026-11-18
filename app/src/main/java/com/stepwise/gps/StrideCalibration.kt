package com.stepwise.gps

sealed class StrideResult {
    data class Success(val strideCm: Double) : StrideResult()
    data class Rejected(val reason: String) : StrideResult()
}

const val MIN_CALIBRATION_DISTANCE_M: Double = 50.0
const val MIN_CALIBRATION_STEPS: Int = 50
const val MIN_PLAUSIBLE_STRIDE_CM: Double = 30.0
const val MAX_PLAUSIBLE_STRIDE_CM: Double = 120.0

/**
 * Derives stride length in cm from a measured walk. Walks that are too short
 * or implausible are rejected so they are never saved.
 */
fun strideFromWalk(distanceM: Double, steps: Int): StrideResult {
    if (distanceM < MIN_CALIBRATION_DISTANCE_M) {
        return StrideResult.Rejected(
            "walk too short: at least ${MIN_CALIBRATION_DISTANCE_M.toInt()} m required",
        )
    }
    if (steps < MIN_CALIBRATION_STEPS) {
        return StrideResult.Rejected(
            "too few steps: at least $MIN_CALIBRATION_STEPS steps required",
        )
    }
    val strideCm = distanceM * 100.0 / steps
    if (strideCm < MIN_PLAUSIBLE_STRIDE_CM || strideCm > MAX_PLAUSIBLE_STRIDE_CM) {
        return StrideResult.Rejected("implausible stride")
    }
    return StrideResult.Success(strideCm)
}
