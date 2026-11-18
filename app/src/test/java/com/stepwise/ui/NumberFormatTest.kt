package com.stepwise.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class NumberFormatTest {

    @Test
    fun formatCountAddsThousandsSeparators() {
        assertEquals("7,880", formatCount(7880L))
        assertEquals("12,345,678", formatCount(12_345_678L))
        assertEquals("0", formatCount(0L))
    }

    @Test
    fun formatKcalRoundsToWholeNumber() {
        assertEquals("1,234", formatKcal(1233.6))
        assertEquals("1,233", formatKcal(1233.4))
        assertEquals("0", formatKcal(0.0))
    }

    @Test
    fun formatPercentHasOneDecimalAndSign() {
        assertEquals("-7.4%", formatPercent(-7.42))
        assertEquals("+3.0%", formatPercent(3.0))
        assertEquals("+0.0%", formatPercent(0.0))
    }

    @Test
    fun helpersIgnoreGermanDefaultLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("7,880", formatCount(7880L))
            assertEquals("1,234", formatKcal(1234.0))
            assertEquals("-7.4%", formatPercent(-7.4))
        } finally {
            Locale.setDefault(original)
        }
    }
}
