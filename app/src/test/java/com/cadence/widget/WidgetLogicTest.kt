package com.cadence.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLogicTest {

    @Test
    fun ringPercentIsFloored() {
        assertEquals(33, goalRingLabel(3_333, 10_000).percent)
        assertEquals(50, goalRingLabel(5_000, 10_000).percent)
        assertEquals(0, goalRingLabel(0, 10_000).percent)
    }

    @Test
    fun ringPercentIsNotCappedButFillIsCapped() {
        val over = goalRingLabel(15_000, 10_000)
        assertEquals(150, over.percent)
        assertEquals(1f, over.fraction, 0f)
        assertEquals(0.25f, goalRingLabel(2_500, 10_000).fraction, 0.0001f)
    }

    @Test
    fun ringWithoutGoalHasNoPercentOrFill() {
        val none = goalRingLabel(4_000, 0)
        assertFalse(none.hasGoal)
        assertEquals(0, none.percent)
        assertEquals(0f, none.fraction, 0f)
        assertEquals("", none.text)
    }

    @Test
    fun negativeStepsCountAsZero() {
        val label = goalRingLabel(-10, 1_000)
        assertEquals(0, label.percent)
        assertEquals("0 / 1k", label.text)
    }

    @Test
    fun ringTextUsesCompactCounts() {
        assertEquals("6.5k / 10k", goalRingLabel(6_500, 10_000).text)
        assertEquals("950 / 1.2k", goalRingLabel(950, 1_200).text)
        assertEquals("1.2M / 2M", goalRingLabel(1_200_000, 2_000_000).text)
        assertTrue(goalRingLabel(1, 10).hasGoal)
    }

    @Test
    fun compactCountTrimsTrailingZero() {
        assertEquals("999", compactCount(999))
        assertEquals("1k", compactCount(1_000))
        assertEquals("12k", compactCount(12_000))
    }

    @Test
    fun actionLabelFollowsCountingState() {
        assertEquals("Start", actionLabel(false))
        assertEquals("Stop", actionLabel(true))
    }

    @Test
    fun actionLabelUsesGivenText() {
        assertEquals("Gehen", actionLabel(false, startText = "Gehen", stopText = "Halt"))
        assertEquals("Halt", actionLabel(true, startText = "Gehen", stopText = "Halt"))
    }
}
