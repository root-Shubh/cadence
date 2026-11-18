package com.cadence.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppStateTest {

    @Test
    fun profileMissingAndNotTracking() {
        assertEquals(
            "Add your profile and start counting to track today",
            todayStatusText(profileMissing = true, trackingActive = false),
        )
    }

    @Test
    fun profileMissingAndTracking() {
        assertEquals(
            "Add your profile to see calories",
            todayStatusText(profileMissing = true, trackingActive = true),
        )
    }

    @Test
    fun profileSetAndNotTracking() {
        assertEquals(
            "Start counting to track today",
            todayStatusText(profileMissing = false, trackingActive = false),
        )
    }

    @Test
    fun profileSetAndTracking() {
        assertEquals("Counting today", todayStatusText(profileMissing = false, trackingActive = true))
    }
}
