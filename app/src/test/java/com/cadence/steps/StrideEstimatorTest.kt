package com.cadence.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrideEstimatorTest {

    /** 5 km/h at 1.8 steps per second is about 77.2 cm per step. */
    private val speed = 5.0
    private val cadence = 1.8
    private val expectedCm = (speed / 3.6) / cadence * 100.0

    /** Feeds one fix per second from [from] to [to] seconds inclusive; returns the accepted samples. */
    private fun walk(
        e: StrideEstimator,
        from: Int,
        to: Int,
        kmh: Double = speed,
        accuracy: Double = 5.0,
        cadenceHz: Double? = cadence,
    ): List<Double> = (from..to).mapNotNull { s -> e.onFix(kmh, accuracy, s * 1000L, cadenceHz) }

    @Test
    fun noSampleBeforeThirtySecondsSustained() {
        val e = StrideEstimator()
        assertEquals(emptyList<Double>(), walk(e, 0, 29))
        val samples = walk(e, 30, 30)
        assertEquals(1, samples.size)
        assertEquals(expectedCm, samples.single(), 0.01)
    }

    @Test
    fun badFixesGiveNoSample() {
        val e = StrideEstimator()
        assertEquals(emptyList<Double>(), walk(e, 0, 40, accuracy = 15.0))
        assertEquals(emptyList<Double>(), walk(e, 41, 80, kmh = 10.0))
        assertEquals(emptyList<Double>(), walk(e, 81, 120, cadenceHz = 1.0))
        assertEquals(emptyList<Double>(), walk(e, 121, 160, cadenceHz = null))
        assertEquals(0, e.samples)
    }

    @Test
    fun usableOnlyAfterTwentySamples() {
        val e = StrideEstimator()
        walk(e, 0, 48) // samples at 30..48: 19 samples
        assertEquals(19, e.samples)
        assertNull(e.usableCm())
        walk(e, 49, 49)
        assertEquals(20, e.samples)
        assertEquals(expectedCm, e.usableCm()!!, 0.01)
    }

    @Test
    fun outlierSampleIsRejected() {
        val e = StrideEstimator()
        walk(e, 0, 40) // 11 samples near 77 cm
        // A cadence that gives about 110 cm: far from the average, so rejected.
        val outlier = walk(e, 41, 45, cadenceHz = (speed / 3.6) / 1.10)
        assertEquals(emptyList<Double>(), outlier)
        assertEquals(11, e.samples)
    }

    @Test
    fun separateWalksAreCountedSeparately() {
        val e = StrideEstimator()
        walk(e, 0, 35)
        walk(e, 36, 60, kmh = 0.5) // a pause ends the first walk
        walk(e, 61, 100)
        assertEquals(2, e.walks)
        assertTrue(e.samples > 2)
    }

    @Test
    fun restoredEstimatorKeepsItsState() {
        val e = StrideEstimator(initialCm = 72.0, initialSamples = 25, initialWalks = 3)
        assertEquals(72.0, e.usableCm()!!, 0.001)
        assertEquals(3, e.walks)
    }
}
