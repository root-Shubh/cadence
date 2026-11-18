package com.cadence.steps

import kotlin.math.abs

/** Walking must be sustained this long, in milliseconds, before its fixes give stride samples. */
const val STRIDE_SUSTAINED_MS: Long = 30_000L

/** Stride samples used from the learned value only once this many are accepted. */
const val STRIDE_MIN_SAMPLES_TO_USE: Int = 20

/** Fixes must be at least this accurate to give a stride sample, in metres. */
const val STRIDE_MAX_ACCURACY_M: Double = 10.0

/** Walking speed range for a stride sample, in km/h. */
const val STRIDE_MIN_SPEED_KMH: Double = 2.0
const val STRIDE_MAX_SPEED_KMH: Double = 7.0

/** Step cadence range for a stride sample, in steps per second. */
const val STRIDE_MIN_CADENCE_HZ: Double = 1.2
const val STRIDE_MAX_CADENCE_HZ: Double = 2.5

/** A single stride sample must lie in this range, in centimetres. Anything else is rejected. */
const val STRIDE_MIN_SAMPLE_CM: Double = 40.0
const val STRIDE_MAX_SAMPLE_CM: Double = 120.0

/** Once this many samples exist, a sample more than this fraction away from the average is an outlier. */
const val STRIDE_OUTLIER_FRACTION: Double = 0.25
const val STRIDE_OUTLIER_CHECK_SAMPLES: Int = 5

/** Cap on the averaging weight, so the learned value keeps adapting slowly over time. */
const val STRIDE_MAX_WEIGHT: Int = 500

/**
 * Learns the user's stride from GPS walking. For each accurate fix at walking speed, with a step
 * cadence measured by the step detector, the stride sample is speed / cadence. Accepted samples
 * feed a running weighted average. Pure Kotlin, no Android dependencies.
 *
 * [learnedCm], [samples] and [walks] can be restored from storage through the constructor.
 */
class StrideEstimator(
    initialCm: Double? = null,
    initialSamples: Int = 0,
    initialWalks: Int = 0,
) {
    /** Running average stride in cm, or null when nothing was learned yet. */
    var learnedCm: Double? = initialCm?.takeIf { it > 0.0 && initialSamples > 0 }
        private set

    /** Number of accepted stride samples. */
    var samples: Int = initialSamples.coerceAtLeast(0)
        private set

    /** Number of separate sustained walks that gave at least one accepted sample. */
    var walks: Int = initialWalks.coerceAtLeast(0)
        private set

    private var weight: Int = minOf(samples, STRIDE_MAX_WEIGHT)
    private var walkStartMillis: Long? = null
    private var walkHasSample = false

    /**
     * The learned stride in cm for distance, or null until [STRIDE_MIN_SAMPLES_TO_USE] samples
     * have been accepted. Below that, the height-based estimate applies.
     */
    fun usableCm(): Double? = learnedCm?.takeIf { samples >= STRIDE_MIN_SAMPLES_TO_USE }

    /**
     * Feeds one GPS fix with the current step cadence in steps per second (null when unknown).
     * Returns the accepted stride sample in cm, or null when the fix gives no sample.
     */
    fun onFix(speedKmh: Double, accuracyM: Double, timeMillis: Long, cadenceHz: Double?): Double? {
        val cadence = cadenceHz?.takeIf { it in STRIDE_MIN_CADENCE_HZ..STRIDE_MAX_CADENCE_HZ }
        val qualifies = accuracyM <= STRIDE_MAX_ACCURACY_M &&
            speedKmh in STRIDE_MIN_SPEED_KMH..STRIDE_MAX_SPEED_KMH
        if (!qualifies || cadence == null) {
            walkStartMillis = null
            walkHasSample = false
            return null
        }

        val start = walkStartMillis ?: timeMillis.also { walkStartMillis = it }
        if (timeMillis - start < STRIDE_SUSTAINED_MS) return null

        val strideCm = (speedKmh / 3.6) / cadence * 100.0
        if (strideCm !in STRIDE_MIN_SAMPLE_CM..STRIDE_MAX_SAMPLE_CM) return null

        val average = learnedCm
        if (average != null && samples >= STRIDE_OUTLIER_CHECK_SAMPLES &&
            abs(strideCm - average) / average > STRIDE_OUTLIER_FRACTION
        ) {
            return null
        }

        learnedCm = if (average == null) strideCm else (average * weight + strideCm) / (weight + 1)
        weight = minOf(weight + 1, STRIDE_MAX_WEIGHT)
        samples++
        if (!walkHasSample) {
            walkHasSample = true
            walks++
        }
        return strideCm
    }
}
