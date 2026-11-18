package com.cadence.patterns

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.PI
import kotlin.math.abs

class WalkTrackTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Before
    fun setUp() = LiveTrack.resetForTest()

    @After
    fun tearDown() = LiveTrack.resetForTest()

    // Row-major rotation matrices, device to world (east, north, up).
    private val flatTopNorth = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    // Upright, screen facing south (towards the user), back of the phone facing north.
    private val uprightFacingNorth = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)

    private fun near(expected: Double, actual: Double) = assertTrue("$expected vs $actual", abs(expected - actual) < 1e-6)

    @Test
    fun flatPhoneHeadsWhereTheTopEdgePoints() {
        near(0.0, forwardHeading(flatTopNorth)!!)
    }

    @Test
    fun uprightPhoneHeadsWhereTheBackFaces() {
        near(0.0, forwardHeading(uprightFacingNorth)!!)
    }

    @Test
    fun flatPhoneTurnedEastHeadsEast() {
        // Device y axis points east: column 1 is (1, 0, 0).
        val r = floatArrayOf(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
        near(PI / 2, forwardHeading(r)!!)
    }

    @Test
    fun noHeadingWhenForwardCannotBeTold() {
        // Device y up and z also on the vertical axis: the forward projection is zero.
        val r = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f)
        assertNull(forwardHeading(r))
        assertNull(forwardHeading(FloatArray(4)))
    }

    @Test
    fun stepsBuildTheShapeWalked() {
        val day = "2026-11-19"
        repeat(3) { LiveTrack.onStep(day, it.toLong(), 0.0, 1.0) } // north 3 m
        repeat(2) { LiveTrack.onStep(day, 10L + it, PI / 2, 1.0) } // east 2 m

        val points = LiveTrack.points.value
        assertEquals(6, points.size) // start point plus five steps
        val last = points.last()
        near(2.0, last.x)
        near(3.0, last.y)
        assertFalse(points.first().pen)
    }

    @Test
    fun penUpStepsMoveWithoutDrawing() {
        val day = "2026-11-19"
        LiveTrack.onStep(day, 0L, 0.0, 1.0)
        LiveTrack.setPenDown(false)
        LiveTrack.onStep(day, 1L, 0.0, 1.0)
        assertFalse(LiveTrack.points.value.last().pen)
        near(2.0, LiveTrack.points.value.last().y)
    }

    @Test
    fun newDrawingStartsAtTheOrigin() {
        val day = "2026-11-19"
        repeat(3) { LiveTrack.onStep(day, it.toLong(), 0.0, 1.0) }
        LiveTrack.newDrawing()
        LiveTrack.onStep(day, 5L, 0.0, 1.0)

        val latest = latestDrawing(LiveTrack.points.value)
        assertEquals(2, latest.size)
        near(0.0, latest.first().y)
        near(1.0, latest.last().y)
        assertEquals(1, latest.last().drawing)
    }

    @Test
    fun stepWithoutHeadingIsSkippedAndNewDayStartsEmpty() {
        LiveTrack.onStep("2026-11-19", 0L, null, 1.0)
        assertTrue(LiveTrack.points.value.isEmpty())
        LiveTrack.onStep("2026-11-19", 1L, 0.0, 1.0)
        LiveTrack.onStep("2026-11-20", 2L, 0.0, 1.0)
        assertEquals(2, LiveTrack.points.value.size)
        assertEquals("2026-11-20", LiveTrack.day())
    }

    @Test
    fun pendingPointsAreDrainedOnceAndCanBeRequeued() {
        LiveTrack.onStep("2026-11-19", 0L, 0.0, 1.0)
        val drained = LiveTrack.drainPending()
        assertEquals(2, drained.size)
        assertTrue(LiveTrack.drainPending().isEmpty())
        LiveTrack.requeue(drained)
        assertEquals(2, LiveTrack.drainPending().size)
    }

    @Test
    fun fitKeepsShapeInsideTheCanvasAndScalesWithTheWalk() {
        val square = listOf(
            TrackPoint(0, 0.0, 0.0, true, 0),
            TrackPoint(1, 10.0, 0.0, true, 0),
            TrackPoint(2, 10.0, 10.0, true, 0),
        )
        val fitted = fitTrack(square, width = 220f, height = 220f, padding = 10f)
        fitted.forEach { assertTrue(it.x in 10f..210f && it.y in 10f..210f) }
        // North is up: the north-east corner is drawn above and right of the origin.
        assertTrue(fitted[2].y < fitted[1].y)
        assertTrue(fitted[1].x > fitted[0].x)
        // 10 m fills the 200 px usable width.
        near(200.0, (fitted[1].x - fitted[0].x).toDouble())
    }

    @Test
    fun smallWalksAreShownAtTheMinimumSpan() {
        val tiny = listOf(TrackPoint(0, 0.0, 0.0, true, 0), TrackPoint(1, 1.0, 0.0, true, 0))
        val fitted = fitTrack(tiny, width = 400f, height = 400f, padding = 0f)
        near(100.0, (fitted[1].x - fitted[0].x).toDouble()) // 1 m of a 4 m span
    }

    @Test
    fun strideUsesOverrideThenLearnedThenHeightThenDefault() {
        near(0.8, drawStrideMeters(overrideCm = 80.0, learnedCm = 70.0, heightCm = 180.0))
        near(0.7, drawStrideMeters(overrideCm = null, learnedCm = 70.0, heightCm = 180.0))
        near(0.747, drawStrideMeters(overrideCm = null, learnedCm = null, heightCm = 180.0))
        near(DEFAULT_DRAW_STRIDE_M, drawStrideMeters(null, null, null))
        // Nonsense values are skipped: height 0 (no profile), a 5 m override.
        near(DEFAULT_DRAW_STRIDE_M, drawStrideMeters(overrideCm = 500.0, learnedCm = null, heightCm = 0.0))
    }

    @Test
    fun codecRoundTripsAndSkipsBadLines() {
        val p = TrackPoint(123L, 1.5, -2.25, true, 3)
        assertEquals(p, TrackCodec.decode(TrackCodec.encode(p)))
        val decoded = TrackCodec.decodeAll("${TrackCodec.encode(p)}\ngarbage\n1,2,3\n5,NaN,1,1,0\n")
        assertEquals(listOf(p), decoded)
    }

    @Test
    fun storeAppendsAndReadsBack() {
        val store = TrackStore(temp.newFolder())
        val a = TrackPoint(1L, 0.0, 0.0, false, 0)
        val b = TrackPoint(2L, 0.0, 0.7, true, 0)
        store.append("me", "2026-11-19", listOf(a))
        store.append("me", "2026-11-19", listOf(b))
        assertEquals(listOf(a, b), store.read("me", "2026-11-19"))
        assertEquals(listOf("2026-11-19"), store.listDates("me"))
        assertTrue(store.read("me", "2026-11-18").isEmpty())
    }
}
