package com.cadence.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalInputTest {

    @Test
    fun blankIsEmpty() {
        assertEquals(GoalInput.Empty, parseGoalInput(""))
    }

    @Test
    fun whitespaceOnlyIsEmpty() {
        assertEquals(GoalInput.Empty, parseGoalInput("   "))
        assertEquals(GoalInput.Empty, parseGoalInput("\t\n"))
    }

    @Test
    fun plainNumberIsValid() {
        assertEquals(GoalInput.Valid(7000), parseGoalInput("7000"))
        assertEquals(GoalInput.Valid(7000), parseGoalInput(" 7000 "))
    }

    @Test
    fun commaSeparatedIsValid() {
        assertEquals(GoalInput.Valid(7000), parseGoalInput("7,000"))
        assertEquals(GoalInput.Valid(1000000), parseGoalInput("1,000,000"))
    }

    @Test
    fun spaceSeparatedIsValid() {
        assertEquals(GoalInput.Valid(7000), parseGoalInput("7 000"))
    }

    @Test
    fun zeroIsInvalid() {
        assertEquals(GoalInput.Invalid, parseGoalInput("0"))
    }

    @Test
    fun negativeIsInvalid() {
        assertEquals(GoalInput.Invalid, parseGoalInput("-5"))
    }

    @Test
    fun decimalIsInvalid() {
        assertEquals(GoalInput.Invalid, parseGoalInput("12.5"))
    }

    @Test
    fun nonNumericIsInvalid() {
        assertEquals(GoalInput.Invalid, parseGoalInput("abc"))
        assertEquals(GoalInput.Invalid, parseGoalInput("7k"))
    }

    @Test
    fun aboveMaximumIsInvalid() {
        assertEquals(GoalInput.Invalid, parseGoalInput("2000000"))
        assertEquals(GoalInput.Invalid, parseGoalInput("1,000,001"))
    }

    @Test
    fun badGroupingIsInvalid() {
        assertEquals(GoalInput.Invalid, parseGoalInput("70,00"))
    }

    @Test
    fun validCarriesParsedNumber() {
        val result = parseGoalInput("12,345")
        assertEquals(GoalInput.Valid(12345), result)
        assertEquals(12345, (result as GoalInput.Valid).value)
    }
}
