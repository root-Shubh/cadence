package com.cadence.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class GoalCardColorsTest {

    private val pairs = listOf(
        "light weekly" to (GoalCardTokens.WeeklyContainerLight to GoalCardTokens.WeeklyOnLight),
        "dark weekly" to (GoalCardTokens.WeeklyContainerDark to GoalCardTokens.WeeklyOnDark),
        "light monthly" to (GoalCardTokens.MonthlyContainerLight to GoalCardTokens.MonthlyOnLight),
        "dark monthly" to (GoalCardTokens.MonthlyContainerDark to GoalCardTokens.MonthlyOnDark),
        "light daily" to (LightPalette.primaryContainer to LightPalette.onPrimaryContainer),
        "dark daily" to (DarkPalette.primaryContainer to DarkPalette.onPrimaryContainer),
    )

    @Test
    fun cardTextMeetsAtLeast4Point5Contrast() {
        for ((name, pair) in pairs) {
            val ratio = contrastRatio(pair.second, pair.first)
            assertTrue("$name contrast was $ratio", ratio >= 4.5)
        }
    }
}
