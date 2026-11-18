package com.cadence.steps

import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Counts steps from a stream of accelerometer magnitudes. Pure Kotlin, no Android dependencies,
 * so it can be unit tested on the JVM.
 *
 * The caller passes the length of the acceleration vector, sqrt(x^2 + y^2 + z^2), in m/s^2 for
 * each sample. The detector:
 * 1. removes the slowly moving gravity baseline (a moving average over [BASELINE_WINDOW_SECONDS]);
 * 2. sets a dynamic threshold T = max([MIN_THRESHOLD], [THRESHOLD_K] * sd), where sd is the spread
 *    of the deviation over the last [SPREAD_WINDOW_SECONDS], so gentle and hard walkers both get a
 *    threshold that fits their motion;
 * 3. counts a step on each local maximum above T, with hysteresis (the signal must fall below
 *    T * [REARM_FRACTION] before another peak can count) and a minimum interval between steps.
 *
 * A step is stamped with the time of its peak, which is one sample before the sample that
 * confirms it.
 *
 * @param sampleRateHz the expected sample rate. It only sets the sizes of the windows, in samples.
 */
class StepDetector(sampleRateHz: Double = 50.0) {

    private val baselineSize: Int = max(1, round(sampleRateHz * BASELINE_WINDOW_SECONDS).toInt())
    private val baseline = DoubleArray(baselineSize)
    private var baselineFilled = 0
    private var baselineNext = 0
    private var baselineSum = 0.0

    private val spreadSize: Int = max(2, round(sampleRateHz * SPREAD_WINDOW_SECONDS).toInt())
    private val deviations = DoubleArray(spreadSize)
    private var deviationsFilled = 0
    private var deviationsNext = 0

    // The two deviations before the current sample, used to find a peak one sample late.
    private var samplesSeen = 0
    private var previousDeviation = 0.0
    private var previousTimeMillis = 0L
    private var deviationBeforePrevious = 0.0

    /** False from a counted step until the signal falls below the re-arm level. */
    private var armed = true
    private var lastStepMillis: Long? = null

    /**
     * Feeds one sample and returns 1 if a step's peak was confirmed at this sample, otherwise 0.
     * A returned step is stamped with the peak time, not with [timeMillis].
     *
     * @param magnitude vector length of the acceleration in m/s^2.
     * @param timeMillis sample time in milliseconds. It must not go backwards.
     */
    fun onSample(magnitude: Float, timeMillis: Long): Int {
        val value = magnitude.toDouble()
        updateBaseline(value)
        val deviation = value - baselineSum / baselineFilled
        updateDeviations(deviation)
        val threshold = dynamicThreshold()

        var steps = 0
        if (armed && samplesSeen >= 2 && isPeak(deviation) && previousDeviation > threshold) {
            val last = lastStepMillis
            if (last == null || previousTimeMillis - last >= MIN_STEP_INTERVAL_MS_EFFECTIVE) {
                armed = false
                lastStepMillis = previousTimeMillis
                steps = 1
            }
        }
        if (!armed && deviation < threshold * REARM_FRACTION) armed = true

        deviationBeforePrevious = previousDeviation
        previousDeviation = deviation
        previousTimeMillis = timeMillis
        samplesSeen++
        return steps
    }

    /** True when the previous sample is a local maximum: above the sample before it, not below the current one. */
    private fun isPeak(current: Double): Boolean =
        previousDeviation > deviationBeforePrevious && previousDeviation >= current

    private fun updateBaseline(value: Double) {
        if (baselineFilled == baselineSize) {
            baselineSum -= baseline[baselineNext]
        } else {
            baselineFilled++
        }
        baseline[baselineNext] = value
        baselineSum += value
        baselineNext = (baselineNext + 1) % baselineSize
    }

    private fun updateDeviations(deviation: Double) {
        deviations[deviationsNext] = deviation
        deviationsNext = (deviationsNext + 1) % spreadSize
        if (deviationsFilled < spreadSize) deviationsFilled++
    }

    /** T = max(MIN_THRESHOLD, THRESHOLD_K * sd) over the spread window. */
    private fun dynamicThreshold(): Double {
        val n = deviationsFilled
        var sum = 0.0
        for (i in 0 until n) sum += deviations[i]
        val mean = sum / n
        var squares = 0.0
        for (i in 0 until n) {
            val d = deviations[i] - mean
            squares += d * d
        }
        val sd = sqrt(squares / n)
        return max(MIN_THRESHOLD, THRESHOLD_K * sd)
    }

    companion object {
        /**
         * Length of the moving-average window for the gravity baseline, in seconds. About one
         * second averages out a walking stride while still following slow changes in orientation.
         */
        const val BASELINE_WINDOW_SECONDS = 1.0

        /** Length of the window over which the spread (standard deviation) is measured, in seconds. */
        const val SPREAD_WINDOW_SECONDS = 2.0

        /** Scale factor k on the spread: the threshold is k times the standard deviation. */
        const val THRESHOLD_K = 0.6

        /**
         * The smallest threshold, in m/s^2. A still phone has a spread well below this, so its
         * noise never reaches the threshold and is not counted.
         */
        const val MIN_THRESHOLD = 0.8

        /**
         * After a step, the deviation must fall below threshold * REARM_FRACTION before another
         * step can count. This hysteresis stops ripples near the threshold counting twice.
         */
        const val REARM_FRACTION = 0.5

        /** Shortest time between two steps, in milliseconds. A closer peak is rejected. */
        const val MIN_STEP_INTERVAL_MS = 250L

        /** The highest plausible step rate, in steps per second. */
        const val MAX_STEPS_PER_SECOND = 3.5

        /**
         * The minimum interval enforced between steps: the stricter of [MIN_STEP_INTERVAL_MS] and
         * the interval implied by [MAX_STEPS_PER_SECOND] (about 286 ms).
         */
        val MIN_STEP_INTERVAL_MS_EFFECTIVE: Long =
            max(MIN_STEP_INTERVAL_MS, (1000.0 / MAX_STEPS_PER_SECOND).toLong())
    }
}
