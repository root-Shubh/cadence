package com.stepwise.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileFormatTest {

    @Test
    fun nullIsEmpty() {
        assertEquals("", formatMeasure(null))
    }

    @Test
    fun wholeNumberHasNoDecimals() {
        assertEquals("175", formatMeasure(175.0))
    }

    @Test
    fun nearWholeNumberRoundsToWhole() {
        assertEquals("175", formatMeasure(175.04))
    }

    @Test
    fun keepsOneDecimal() {
        assertEquals("70.5", formatMeasure(70.5))
    }
}
