package com.cadence.steps

/**
 * Live total for today when TYPE_STEP_DETECTOR is the step source: the stored base plus the
 * steps the detector reported since counting started. Never below [base], clamped to the Int
 * range. Pure Kotlin, no Android dependencies.
 */
fun liveFromDetector(base: Int, detectedSinceStart: Long): Int {
    val start = base.toLong().coerceAtLeast(0L)
    val detected = detectedSinceStart.coerceAtLeast(0L)
    return (start + detected).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/** A TYPE_STEP_DETECTOR event reports one step, with values[0] equal to 1.0f. */
fun isDetectedStep(value0: Float): Boolean = value0 == 1.0f
