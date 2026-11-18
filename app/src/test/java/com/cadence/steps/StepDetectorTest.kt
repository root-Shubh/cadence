package com.cadence.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class StepDetectorTest {

    private val gravity = 9.81f
    private val sampleRateHz = 50.0
    private val periodMs = 1000.0 / sampleRateHz

    /** Feeds [count] samples of [valueAt] (seconds to m/s^2) and returns the step times. */
    private fun run(
        count: Int,
        valueAt: (Double) -> Float,
    ): Pair<Int, List<Long>> {
        val detector = StepDetector(sampleRateHz)
        val times = mutableListOf<Long>()
        for (i in 0 until count) {
            val tMillis = Math.round(i * periodMs)
            val steps = detector.onSample(valueAt(tMillis / 1000.0), tMillis)
            if (steps == 1) times += tMillis
        }
        return times.size to times
    }

    /** A sine walk at [hz] with [amplitude] m/s^2 around gravity, plus uniform noise of +/- [noise]. */
    private fun sineWalk(hz: Double, amplitude: Double, noise: Double = 0.0, seed: Int = 7): (Double) -> Float {
        val random = Random(seed)
        return { t ->
            val jitter = if (noise > 0.0) random.nextDouble(-noise, noise) else 0.0
            (gravity + amplitude * sin(2 * PI * hz * t) + jitter).toFloat()
        }
    }

    private fun assertCountNear(expected: Int, actual: Int, label: String) {
        val tolerance = expected * 0.10
        assertTrue(
            "$label: expected $expected within 10% but was $actual",
            abs(actual - expected) <= tolerance,
        )
    }

    /** A 1.5 Hz walk with noise, 10 s: about 15 steps. */
    @Test
    fun walkAt1Point5HzWithNoiseCountsWithinTenPercent() {
        val (steps, _) = run(count = 500, valueAt = sineWalk(1.5, 3.0, noise = 0.1))
        assertCountNear(15, steps, "1.5 Hz walk")
    }

    /** A 2.0 Hz walk with noise, 30 s: about 60 steps. */
    @Test
    fun walkAt2HzWithNoiseCountsWithinTenPercent() {
        val (steps, _) = run(count = 1500, valueAt = sineWalk(2.0, 3.0, noise = 0.1))
        assertCountNear(60, steps, "2.0 Hz walk")
    }

    /** Standing still with noise well below the threshold floor: no steps. */
    @Test
    fun stillPhoneWithNoiseCountsZero() {
        val random = Random(seed = 42)
        val (steps, _) = run(count = 1000) { gravity + random.nextDouble(-0.2, 0.2).toFloat() }
        assertEquals(0, steps)
    }

    /** Constant gravity only, no motion: no steps. */
    @Test
    fun constantGravityCountsZero() {
        val (steps, _) = run(count = 500) { gravity }
        assertEquals(0, steps)
    }

    /** A phone shaken hard at 8 Hz for 2 s: at most 3.5 steps per second, so at most 7 in 2 s. */
    @Test
    fun hardShakeAt8HzIsCappedAtThreeAndAHalfStepsPerSecond() {
        val (steps, times) = run(count = 100, valueAt = sineWalk(8.0, 5.0))
        assertTrue("shake gave $steps steps in 2 s, expected at most 7", steps <= 7)
        assertTrue("expected the shake to produce steps", steps > 0)
        for (i in times.indices) {
            for (j in i + 1 until times.size) {
                assertTrue("steps ${times[i]} and ${times[j]} are closer than 286 ms", times[j] - times[i] >= 286)
            }
        }
    }

    /** A slow walk at 0.8 Hz for 20 s: about 16 steps. */
    @Test
    fun slowWalkAt0Point8HzCounts() {
        val (steps, _) = run(count = 1000, valueAt = sineWalk(0.8, 3.0))
        assertCountNear(16, steps, "0.8 Hz walk")
    }

    /** A very small stride at 1.8 Hz with low amplitude (1 m/s^2) and light noise: still counted. */
    @Test
    fun smallStrideAt1Point8HzWithLowAmplitudeStillCounts() {
        val (steps, _) = run(count = 500, valueAt = sineWalk(1.8, 1.0, noise = 0.05))
        assertCountNear(18, steps, "small stride 1.8 Hz")
    }

    /** A 4 Hz signal is capped at 3.5 steps per second over 10 s. */
    @Test
    fun fastSignalIsCappedAtThreeAndAHalfStepsPerSecond() {
        val (steps, times) = run(count = 500, valueAt = sineWalk(4.0, 3.0))
        assertTrue("steps were $steps, expected at most 35 in 10 s", steps <= 35)
        assertTrue("expected the signal to produce steps", steps > 0)
        for (windowStart in 0L..9000L step 100L) {
            val inWindow = times.count { it >= windowStart && it < windowStart + 1000L }
            assertTrue("$inWindow steps in one second starting at $windowStart ms", inWindow <= 4)
        }
    }

    /** Two peaks 100 ms apart count once. */
    @Test
    fun peaksLessThan250MsApartCountOnce() {
        val (steps, _) = run(count = 300) { tSeconds ->
            val ms = Math.round(tSeconds * 1000.0)
            if (ms == 2000L || ms == 2100L) gravity + 3.0f else gravity
        }
        assertEquals(1, steps)
    }

    /** Control: two peaks 400 ms apart both count. */
    @Test
    fun peaksMoreThan250MsApartCountTwice() {
        val (steps, _) = run(count = 300) { tSeconds ->
            val ms = Math.round(tSeconds * 1000.0)
            if (ms == 2000L || ms == 2400L) gravity + 3.0f else gravity
        }
        assertEquals(2, steps)
    }

    /**
     * Synthetic 2 Hz walk with a short bump at each foot strike: over 30 s the count is within 10%
     * of 60, and each step is within 500 ms of its foot strike.
     */
    @Test
    fun twoHertzBumpWalkCountsWithinTenPercentAndWithinHalfSecond() {
        val strikesMs = (0 until 60).map { 250L + it * 500L }
        val bumpHalfWidthMs = 150.0
        val (steps, times) = run(count = 30 * 50) { tSeconds ->
            val ms = tSeconds * 1000.0
            val bump = strikesMs.maxOf { strike ->
                val d = ms - strike
                if (abs(d) < bumpHalfWidthMs) {
                    val c = cos(PI * d / (2 * bumpHalfWidthMs))
                    c * c
                } else {
                    0.0
                }
            }
            (gravity + 4.0 * bump).toFloat()
        }
        assertCountNear(60, steps, "bump walk")
        for (t in times) {
            val nearest = strikesMs.minBy { abs(it - t) }
            val lag = t - nearest
            assertTrue("step at $t ms is $lag ms from its foot strike", abs(lag) <= 500)
        }
    }

    /** Warm-up: a 1.8 Hz walk starting at t=0 has its first 3 steps within the first 3 seconds. */
    @Test
    fun warmUpCountsFirstThreeStepsWithinThreeSeconds() {
        val (_, times) = run(count = 200, valueAt = sineWalk(1.8, 3.0, noise = 0.05))
        val early = times.filter { it < 3000L }
        assertTrue("steps were $times, expected 3 within 3 s", early.size >= 3)
    }

    /** Steady 2 Hz walk after warm-up: no gap between steps longer than two step intervals (1 s). */
    @Test
    fun steadyTwoHzWalkHasNoGapLongerThanTwoIntervals() {
        val (_, times) = run(count = 30 * 50, valueAt = sineWalk(2.0, 3.0, noise = 0.1))
        val afterWarmUp = times.filter { it >= 3000L }
        assertTrue("expected steps after warm-up", afterWarmUp.size >= 2)
        for (i in 1 until afterWarmUp.size) {
            val gap = afterWarmUp[i] - afterWarmUp[i - 1]
            assertTrue("gap of $gap ms between ${afterWarmUp[i - 1]} and ${afterWarmUp[i]} ms", gap <= 1000L)
        }
    }

    /** A 2 s shake followed by a 2 Hz walk: steps count again within 3 s of the shake ending. */
    @Test
    fun recoversAfterShakeBurst() {
        val shake = sineWalk(8.0, 5.0)
        val walk = sineWalk(2.0, 3.0, noise = 0.1)
        val (_, times) = run(count = 10 * 50) { t -> if (t < 2.0) shake(t) else walk(t - 2.0) }
        val recovered = times.filter { it >= 2000L && it < 5000L }
        assertTrue("steps were $times, expected at least 3 within 3 s after the shake", recovered.size >= 3)
    }
}
