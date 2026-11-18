package com.cadence.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Locale

class CsvExportTest {

    private fun row(date: String, steps: Int = 100, distance: Double = 1.5) = CsvDailyRow(
        dateIso = date,
        steps = steps,
        distanceM = distance,
        bmrKcal = 1000.0,
        rmrKcal = 1100.0,
        activeKcal = 50.25,
        totalKcal = 1150.25,
    )

    @Test
    fun headerIsExact() {
        val csv = buildDailyCsv(emptyList())
        assertEquals("date,steps,distance_m,bmr_kcal,rmr_kcal,active_kcal,total_kcal\r\n", csv)
    }

    @Test
    fun emptyListGivesHeaderOnly() {
        val csv = buildDailyCsv(emptyList())
        assertEquals(1, csv.split("\r\n").count { it.isNotEmpty() })
    }

    @Test
    fun rowsAreSortedByDate() {
        val csv = buildDailyCsv(listOf(row("2026-03-02"), row("2026-01-15"), row("2026-02-28")))
        val dates = csv.split("\r\n").drop(1).filter { it.isNotEmpty() }.map { it.substringBefore(',') }
        assertEquals(listOf("2026-01-15", "2026-02-28", "2026-03-02"), dates)
    }

    @Test
    fun duplicateDateKeepsLastRow() {
        val csv = buildDailyCsv(listOf(row("2026-01-01", steps = 10), row("2026-01-01", steps = 99)))
        val lines = csv.split("\r\n").filter { it.isNotEmpty() }
        assertEquals(2, lines.size)
        assertTrue(lines[1].startsWith("2026-01-01,99,"))
    }

    @Test
    fun numbersUseTwoDecimalsAndDotRegardlessOfLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val csv = buildDailyCsv(listOf(row("2026-01-01", distance = 1234.5)))
            val dataLine = csv.split("\r\n")[1]
            assertEquals("2026-01-01,100,1234.50,1000.00,1100.00,50.25,1150.25", dataLine)
            assertFalse(dataLine.contains("1234,50"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun invalidDateThrows() {
        val bad = listOf("2026-1-01", "01-01-2026", "2026-02-30", "2026-01-01T00:00", "", "abcd-ef-gh")
        for (value in bad) {
            try {
                buildDailyCsv(listOf(row(value)))
                fail("Expected IllegalArgumentException for '$value'")
            } catch (expected: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun fileNameFormat() {
        assertEquals("cadence-export-2026-11-18.csv", csvFileName("2026-11-18"))
    }

    @Test
    fun fileNameRejectsInvalidDate() {
        try {
            csvFileName("18/11/2026")
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun crlfLineEndingsAndTrailingCrlf() {
        val csv = buildDailyCsv(listOf(row("2026-01-01"), row("2026-01-02")))
        assertTrue(csv.endsWith("\r\n"))
        assertEquals(3, csv.split("\r\n").size - 1)
        assertFalse(csv.replace("\r\n", "").contains("\n"))
        assertFalse(csv.replace("\r\n", "").contains("\r"))
    }
}
