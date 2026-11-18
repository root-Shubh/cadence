package com.cadence.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvImportTest {

    private val header = "date,steps,distance_m,bmr_kcal,rmr_kcal,active_kcal,total_kcal"

    private fun row(date: String, steps: Int = 100) = CsvDailyRow(
        dateIso = date,
        steps = steps,
        distanceM = 1234.5,
        bmrKcal = 1000.25,
        rmrKcal = 1100.0,
        activeKcal = 50.25,
        totalKcal = 1150.25,
    )

    @Test
    fun roundTripMatchesBuildOutput() {
        val rows = listOf(row("2026-01-15", 5), row("2026-02-28", 0), row("2026-03-02", 12345))
        val parsed = parseDailyCsv(buildDailyCsv(rows))
        assertEquals(rows, parsed.rows)
        assertEquals(emptyList<String>(), parsed.errors)
    }

    @Test
    fun roundTripOfEmptyExport() {
        val parsed = parseDailyCsv(buildDailyCsv(emptyList()))
        assertEquals(emptyList<CsvDailyRow>(), parsed.rows)
        assertEquals(emptyList<String>(), parsed.errors)
    }

    @Test
    fun acceptsLfLineEndings() {
        val text = "$header\n2026-01-01,10,1.00,2.00,3.00,4.00,5.00\n"
        val parsed = parseDailyCsv(text)
        assertEquals(listOf(CsvDailyRow("2026-01-01", 10, 1.0, 2.0, 3.0, 4.0, 5.0)), parsed.rows)
        assertEquals(emptyList<String>(), parsed.errors)
    }

    @Test
    fun emptyFileWithHeaderGivesNoRowsAndNoErrors() {
        val parsed = parseDailyCsv("$header\r\n")
        assertEquals(emptyList<CsvDailyRow>(), parsed.rows)
        assertEquals(emptyList<String>(), parsed.errors)
    }

    @Test
    fun headerWithoutTrailingNewlineIsAccepted() {
        val parsed = parseDailyCsv(header)
        assertEquals(emptyList<CsvDailyRow>(), parsed.rows)
        assertEquals(emptyList<String>(), parsed.errors)
    }

    @Test
    fun missingHeaderReturnsOneError() {
        val parsed = parseDailyCsv("")
        assertEquals(emptyList<CsvDailyRow>(), parsed.rows)
        assertEquals(1, parsed.errors.size)
    }

    @Test
    fun wrongHeaderReturnsNoRowsAndOneError() {
        val text = "day,steps,distance_m,bmr_kcal,rmr_kcal,active_kcal,total_kcal\r\n" +
            "2026-01-01,10,1,2,3,4,5\r\n"
        val parsed = parseDailyCsv(text)
        assertEquals(emptyList<CsvDailyRow>(), parsed.rows)
        assertEquals(1, parsed.errors.size)
        assertTrue(parsed.errors[0].startsWith("Line 1:"))
    }

    @Test
    fun invalidRowsAreSkippedWithLineNumbers() {
        val text = listOf(
            header,
            "2026-01-01,10,1,2,3,4,5",
            "2026-02-30,10,1,2,3,4,5",   // impossible date
            "2026/01/03,10,1,2,3,4,5",   // wrong format
            "2026-01-04,-1,1,2,3,4,5",   // negative steps
            "2026-01-05,1.5,1,2,3,4,5",  // non-integer steps
            "2026-01-06,10,NaN,2,3,4,5", // non-finite
            "2026-01-07,10,1,Infinity,3,4,5",
            "2026-01-08,10,-0.5,2,3,4,5", // negative number
            "2026-01-09,10,1,2,3,4",      // too few fields
            "2026-01-10,10,1,2,3,4,abc",  // not a number
            "2026-01-11,10,1,2,3,4,5",
        ).joinToString("\r\n")
        val parsed = parseDailyCsv(text)
        assertEquals(listOf("2026-01-01", "2026-01-11"), parsed.rows.map { it.dateIso })
        assertEquals(9, parsed.errors.size)
        assertEquals("Line 3: invalid date '2026-02-30' (expected yyyy-MM-dd)", parsed.errors[0])
        assertTrue(parsed.errors[1].startsWith("Line 4:"))
        assertTrue(parsed.errors.last().startsWith("Line 11:"))
    }

    @Test
    fun quotedFieldsWithEmbeddedCommaQuoteAndNewline() {
        val text = "$header\r\n" +
            "\"2026-01-01\",10,\"1.00\",2,3,4,5\r\n" +
            "\"2026-01\r\n-02\",10,1,2,3,4,5\r\n"
        val parsed = parseDailyCsv(text)
        assertEquals(listOf("2026-01-01"), parsed.rows.map { it.dateIso })
        assertEquals(1, parsed.errors.size)
        assertTrue(parsed.errors[0].startsWith("Line 3:"))
    }

    @Test
    fun escapedQuoteInsideQuotedFieldIsUnescaped() {
        val text = "$header\r\n\"2026-01-01\"\"\",10,1,2,3,4,5\r\n"
        val parsed = parseDailyCsv(text)
        assertEquals(emptyList<CsvDailyRow>(), parsed.rows)
        assertEquals(1, parsed.errors.size)
    }

    @Test
    fun blankLinesAreIgnored() {
        val text = "$header\r\n\r\n2026-01-01,10,1,2,3,4,5\r\n\r\n"
        val parsed = parseDailyCsv(text)
        assertEquals(1, parsed.rows.size)
        assertEquals(emptyList<String>(), parsed.errors)
    }

    @Test
    fun unterminatedQuoteIsReported() {
        val parsed = parseDailyCsv("$header\r\n\"2026-01-01,10,1,2,3,4,5\r\n")
        assertEquals(emptyList<CsvDailyRow>(), parsed.rows)
        assertEquals(1, parsed.errors.size)
    }
}
