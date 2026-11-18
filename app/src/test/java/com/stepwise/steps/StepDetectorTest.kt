package com.stepwise.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class StepDetectorTest {

    private val gravity = 9.81f
    private val sampleRateHz = 50.0
    private val periodMs = 1000.0 / sampleRateHz

    /** Feeds [count] samples of [valueAt] (seconds to m/s^2) and returns the steps and their times. */
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

    /** (a) A walking-like 1.5 Hz sine for 10 s counts about one step per cycle. */
    @Test
    fun walkingLikeSineCountsRoughlyOneStepPerCycle() {
        val (steps, _) = run(count = 500) { t ->
            (gravity + 3.0 * sin(2 * PI * 1.5 * t)).toFloat()
        }
        assertTrue("expected 14 to 16 steps but was $steps", steps in 14..16)
    }

    /** (b) Standing still, with only gravity, counts nothing. */
    @Test
    fun constantGravityCountsZero() {
        val (steps, _) = run(count = 500) { gravity }
        assertEquals(0, steps)
    }

    /** (c) Small noise well below the threshold counts nothing. */
    @Test
    fun smallNoiseCountsZero() {
        val random = Random(seed = 42)
        val (steps, _) = run(count = 500) {
            gravity + (random.nextDouble(-0.2, 0.2)).toFloat()
        }
        assertEquals(0, steps)
    }

    /** (d) A 4 Hz signal is capped: never more than 4 steps in any one-second window. */
    @Test
    fun fastSignalIsCappedAtFourStepsPerSecond() {
        val (steps, times) = run(count = 500) { t ->
            (gravity + 3.0 * sin(2 * PI * 4.0 * t)).toFloat()
        }
        assertTrue("steps were $steps, expected at most 40 in 10 s", steps <= 40)
        assertTrue("expected the signal to produce steps", steps > 0)
        for (windowStart in 0L..9000L step 100L) {
            val inWindow = times.count { it >= windowStart && it < windowStart + 1000L }
            assertTrue("$inWindow steps in one second starting at $windowStart ms", inWindow <= 4)
        }
    }

    /** (e) Two peaks 100 ms apart count once. */
    @Test
    fun peaksLessThan250MsApartCountOnce() {
        val (steps, _) = run(count = 300) { tSeconds ->
            val ms = Math.round(tSeconds * 1000.0)
            val peak = ms == 2000L || ms == 2100L
            if (peak) gravity + 3.0f else gravity
        }
        assertEquals(1, steps)
    }

    /** Control for (e): two peaks 400 ms apart both count. */
    @Test
    fun peaksMoreThan250MsApartCountTwice() {
        val (steps, _) = run(count = 300) { tSeconds ->
            val ms = Math.round(tSeconds * 1000.0)
            val peak = ms == 2000L || ms == 2400L
            if (peak) gravity + 3.0f else gravity
        }
        assertEquals(2, steps)
    }
}
