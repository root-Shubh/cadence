package com.cadence.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidationTest {

    private fun valid(
        height: Double? = 170.0,
        weight: Double? = 70.0,
        age: Int? = 30,
        sex: String? = "MALE",
        stride: Double? = null,
    ) = validateProfile(height, weight, age, sex, stride)

    @Test
    fun validProfileHasNoErrors() {
        assertTrue(valid().isEmpty())
        assertTrue(valid(stride = 70.0).isEmpty())
    }

    @Test
    fun heightBoundaries() {
        assertTrue(valid(height = 100.0).isEmpty())
        assertTrue(valid(height = 250.0).isEmpty())
        assertEquals(1, valid(height = 99.9).size)
        assertEquals(1, valid(height = 250.1).size)
    }

    @Test
    fun weightBoundaries() {
        assertTrue(valid(weight = 30.0).isEmpty())
        assertTrue(valid(weight = 300.0).isEmpty())
        assertEquals(1, valid(weight = 29.9).size)
        assertEquals(1, valid(weight = 300.1).size)
    }

    @Test
    fun ageBoundaries() {
        assertTrue(valid(age = 10).isEmpty())
        assertTrue(valid(age = 100).isEmpty())
        assertEquals(1, valid(age = 9).size)
        assertEquals(1, valid(age = 101).size)
    }

    @Test
    fun sexAcceptsOnlyKnownValues() {
        assertTrue(valid(sex = "MALE").isEmpty())
        assertTrue(valid(sex = "FEMALE").isEmpty())
        assertTrue(valid(sex = "OTHER").isEmpty())
        assertEquals(1, valid(sex = "male").size)
        assertEquals(1, valid(sex = "").size)
        assertEquals(1, valid(sex = "X").size)
    }

    @Test
    fun strideOverrideBoundaries() {
        assertTrue(valid(stride = 30.0).isEmpty())
        assertTrue(valid(stride = 120.0).isEmpty())
        assertEquals(1, valid(stride = 29.9).size)
        assertEquals(1, valid(stride = 120.1).size)
    }

    @Test
    fun nullRequiredFieldsAreReported() {
        val errors = validateProfile(null, null, null, null, null)
        assertEquals(4, errors.size)
        assertTrue(errors.all { it.isNotBlank() })
    }

    @Test
    fun nullStrideIsAllowed() {
        assertTrue(valid(stride = null).isEmpty())
    }

    @Test
    fun allInvalidReportsEveryField() {
        val errors = validateProfile(50.0, 500.0, 5, "UNKNOWN", 200.0)
        assertEquals(5, errors.size)
    }
}
