package com.stepwise.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetTextTest {

    @Test
    fun stepsUseThousandsSeparators() {
        assertEquals("0", widgetStepsText(0))
        assertEquals("999", widgetStepsText(999))
        assertEquals("1,000", widgetStepsText(1_000))
        assertEquals("12,345", widgetStepsText(12_345))
        assertEquals("1,234,567", widgetStepsText(1_234_567))
    }

    @Test
    fun negativeStepsShowAsZero() {
        assertEquals("0", widgetStepsText(-5))
    }

    @Test
    fun goalTextShowsGoalWhenSet() {
        assertEquals("of 10,000 goal", widgetGoalText(3_000, 10_000))
        assertEquals("of 500 goal", widgetGoalText(0, 500))
    }

    @Test
    fun goalTextAsksForGoalWhenUnsetOrZero() {
        assertEquals("Set a goal in the app", widgetGoalText(3_000, null))
        assertEquals("Set a goal in the app", widgetGoalText(3_000, 0))
        assertEquals("Set a goal in the app", widgetGoalText(3_000, -1))
    }

    @Test
    fun progressIsStepsWithinGoal() {
        assertEquals(2_500, widgetProgress(2_500, 10_000))
    }

    @Test
    fun progressClampsToZeroAndGoal() {
        assertEquals(0, widgetProgress(-40, 10_000))
        assertEquals(10_000, widgetProgress(12_000, 10_000))
        assertEquals(10_000, widgetProgress(10_000, 10_000))
    }

    @Test
    fun progressIsZeroWhenGoalIsNotPositive() {
        assertEquals(0, widgetProgress(500, 0))
        assertEquals(0, widgetProgress(500, -3))
    }
}
