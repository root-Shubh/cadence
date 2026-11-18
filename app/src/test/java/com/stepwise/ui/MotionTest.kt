package com.stepwise.ui

import com.stepwise.domain.Sex
import com.stepwise.domain.bmrKcal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {

    @Test
    fun staggerDelayGrowsByStepForEarlyItems() {
        assertEquals(0L, staggerDelayMillis(0))
        assertEquals(60L, staggerDelayMillis(1))
        assertEquals(180L, staggerDelayMillis(3))
    }

    @Test
    fun staggerDelayIsCappedAndNeverNegative() {
        assertEquals(480L, staggerDelayMillis(8))
        assertEquals(480L, staggerDelayMillis(100))
        assertEquals(0L, staggerDelayMillis(-5))
    }

    @Test
    fun profileEstimateIsNullUntilFieldsAreValid() {
        assertNull(profileEstimate(null, 70.0, 30, "MALE"))
        assertNull(profileEstimate(175.0, 70.0, 30, null))
        assertNull(profileEstimate(175.0, 70.0, 5, "MALE"))
        assertNull(profileEstimate(175.0, 70.0, 30, "UNKNOWN"))
    }

    @Test
    fun profileEstimateUsesDomainBmr() {
        val estimate = profileEstimate(175.0, 70.0, 30, "MALE")
        assertEquals(Math.round(bmrKcal(70.0, 175.0, 30, Sex.MALE)).toInt(), estimate?.bmrKcal)
        assertTrue((estimate?.dailyRestKcal ?: 0) >= (estimate?.bmrKcal ?: Int.MAX_VALUE))
    }

    @Test
    fun fieldErrorMatchesByPrefix() {
        val errors = listOf("Height is required.", "Sex is required.")
        assertEquals("Height is required.", fieldError(errors, "Height"))
        assertNull(fieldError(errors, "Age"))
    }

    @Test
    fun ungroupedErrorsKeepsOnlyMessagesWithoutAField() {
        val errors = listOf("Age must be between 10 and 100 years.", "Something else.")
        assertEquals(listOf("Something else."), ungroupedErrors(errors))
    }
}
