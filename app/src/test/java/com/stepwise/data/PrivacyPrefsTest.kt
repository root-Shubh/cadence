package com.stepwise.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPrefsTest {

    @Test
    fun missingValueIsNotAcknowledged() {
        assertFalse(acknowledged(null))
    }

    @Test
    fun storedFalseIsNotAcknowledged() {
        assertFalse(acknowledged(false))
    }

    @Test
    fun storedTrueIsAcknowledged() {
        assertTrue(acknowledged(true))
    }
}
