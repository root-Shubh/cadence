package com.stepwise.steps

/**
 * Counts steps from a stream of accelerometer magnitudes. Pure Kotlin, no Android dependencies,
 * so it can be unit tested on the JVM.
 *
 * The caller passes the length of the acceleration vector, sqrt(x^2 + y^2 + z^2), in m/s^2 for
 * each sample. The detector removes the slowly moving gravity baseline, then counts a step on
 * each rising crossing of a threshold, with hysteresis and a minimum interval between steps.
 *
 * @param sampleRateHz the expected sample rate. It only sets the size of the baseline window.
 */
class StepDetector(sampleRateHz: Double = 50.0) {

    private val windowSize: Int = maxOf(1, Math.round(sampleRateHz * BASELINE_WINDOW_SECONDS).toInt())
    private val window = DoubleArray(windowSize)
    private var filled = 0
    private var next = 0
    private var sum = 0.0

    /** True when the signal is below [REARM_LEVEL] again, so the next crossing may count. */
    private var armed = true
    private var lastStepMillis: Long? = null

    /**
     * Feeds one sample and returns 1 if a step is detected at this sample, otherwise 0.
     *
     * @param magnitude vector length of the acceleration in m/s^2.
     * @param timeMillis sample time in milliseconds. It must not go backwards.
     */
    fun onSample(magnitude: Float, timeMillis: Long): Int {
        val value = magnitude.toDouble()
        if (filled == windowSize) {
            sum -= window[next]
        } else {
            filled++
        }
        window[next] = value
        sum += value
        next = (next + 1) % windowSize

        val deviation = value - sum / filled

        if (!armed) {
            if (deviation < REARM_LEVEL) armed = true
            return 0
        }
        if (deviation < STEP_THRESHOLD) return 0

        val last = lastStepMillis
        if (last != null && timeMillis - last < MIN_INTERVAL_MS) return 0

        armed = false
        lastStepMillis = timeMillis
        return 1
    }

    private companion object {
        /**
         * Length of the moving-average window for the gravity baseline, in seconds. About one
         * second covers a full walking stride, so the step motion averages out of the baseline
         * while the slow change in orientation is still followed.
         */
        const val BASELINE_WINDOW_SECONDS = 1.0

        /**
         * How far above the baseline the acceleration must rise to count as a step, in m/s^2.
         * Walking steps give peaks of a few m/s^2. Smaller bumps are treated as noise.
         */
        const val STEP_THRESHOLD = 1.2

        /**
         * After a step, the deviation must fall below this level before another step can
         * count. This hysteresis stops noise around the threshold from counting twice, in m/s^2.
         */
        const val REARM_LEVEL = 0.6

        /**
         * The shortest time allowed between two steps, in milliseconds. A human step takes
         * longer than this, so closer peaks are one step's bounce or sensor noise.
         */
        const val MIN_STEP_INTERVAL_MS = 250L

        /**
         * The highest plausible step rate, in steps per second. It is enforced by the
         * minimum interval: 1000 / MAX_STEPS_PER_SECOND milliseconds between steps.
         */
        const val MAX_STEPS_PER_SECOND = 4

        /** The effective minimum interval: the stricter of the two limits above. */
        val MIN_INTERVAL_MS: Long = maxOf(MIN_STEP_INTERVAL_MS, 1000L / MAX_STEPS_PER_SECOND)
    }
}
