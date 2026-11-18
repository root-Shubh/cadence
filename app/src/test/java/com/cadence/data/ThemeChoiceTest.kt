package com.cadence.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeChoiceTest {

    @Test
    fun nullFallsBackToSystem() {
        assertEquals(ThemeChoice.SYSTEM, parseThemeChoice(null))
    }

    @Test
    fun unknownNameFallsBackToSystem() {
        assertEquals(ThemeChoice.SYSTEM, parseThemeChoice("PURPLE"))
        assertEquals(ThemeChoice.SYSTEM, parseThemeChoice(""))
    }

    @Test
    fun knownNamesParse() {
        assertEquals(ThemeChoice.SYSTEM, parseThemeChoice("SYSTEM"))
        assertEquals(ThemeChoice.LIGHT, parseThemeChoice("LIGHT"))
        assertEquals(ThemeChoice.DARK, parseThemeChoice("DARK"))
    }
}
