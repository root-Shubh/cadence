package com.cadence.export

import org.junit.Assert.assertEquals
import org.junit.Test

class MergeImportTest {

    private fun row(date: String, steps: Int) = CsvDailyRow(date, steps, 0.0, 0.0, 0.0, 0.0, 0.0)

    @Test
    fun importedRowReplacesExistingSameDate() {
        val merged = mergeImported(
            existing = listOf(row("2026-01-01", 10)),
            imported = listOf(row("2026-01-01", 99)),
        )
        assertEquals(listOf(row("2026-01-01", 99)), merged)
    }

    @Test
    fun newDatesAreAddedAndSortedByDate() {
        val merged = mergeImported(
            existing = listOf(row("2026-03-01", 3), row("2026-01-01", 1)),
            imported = listOf(row("2026-02-01", 2)),
        )
        assertEquals(listOf("2026-01-01", "2026-02-01", "2026-03-01"), merged.map { it.dateIso })
        assertEquals(listOf(1, 2, 3), merged.map { it.steps })
    }

    @Test
    fun emptyImportKeepsExistingSorted() {
        val merged = mergeImported(listOf(row("2026-05-01", 5), row("2026-04-01", 4)), emptyList())
        assertEquals(listOf("2026-04-01", "2026-05-01"), merged.map { it.dateIso })
    }

    @Test
    fun emptyExistingReturnsImportSorted() {
        val merged = mergeImported(emptyList(), listOf(row("2026-02-02", 2), row("2026-02-01", 1)))
        assertEquals(listOf("2026-02-01", "2026-02-02"), merged.map { it.dateIso })
    }

    @Test
    fun bothEmptyGivesEmpty() {
        assertEquals(emptyList<CsvDailyRow>(), mergeImported(emptyList(), emptyList()))
    }
}
