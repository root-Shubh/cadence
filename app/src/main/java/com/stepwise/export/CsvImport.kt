package com.stepwise.export

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.TreeMap

/** Result of parsing a daily CSV. [errors] describe skipped rows as "Line N: ...". */
data class CsvImportResult(
    val rows: List<CsvDailyRow>,
    val errors: List<String>,
)

private val IMPORT_HEADER = listOf(
    "date", "steps", "distance_m", "bmr_kcal", "rmr_kcal", "active_kcal", "total_kcal",
)
private val IMPORT_DATE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")
private val IMPORT_STEPS_REGEX = Regex("\\d+")
private val IMPORT_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT)

/**
 * Parses text in the format written by [buildDailyCsv]. Accepts CRLF or LF line endings and
 * RFC 4180 quoted fields. Blank lines are ignored.
 *
 * A missing or wrong header returns no rows and a single error. Each data row is validated
 * (date as yyyy-MM-dd, steps as an integer >= 0, other numbers finite and >= 0); invalid rows
 * are skipped and reported in [CsvImportResult.errors] with their line number.
 */
fun parseDailyCsv(text: String): CsvImportResult {
    val records = splitCsvRecords(text)
    val expectedHeader = IMPORT_HEADER.joinToString(",")
    val first = records.firstOrNull()
        ?: return CsvImportResult(emptyList(), listOf("Missing header: expected $expectedHeader"))
    if (first.error != null || first.fields != IMPORT_HEADER) {
        return CsvImportResult(emptyList(), listOf("Line ${first.line}: wrong header, expected $expectedHeader"))
    }

    val rows = mutableListOf<CsvDailyRow>()
    val errors = mutableListOf<String>()
    for (record in records.drop(1)) {
        if (record.error != null) {
            errors += "Line ${record.line}: ${record.error}"
            continue
        }
        val (row, reason) = parseDataRow(record.fields)
        if (row != null) rows += row else errors += "Line ${record.line}: $reason"
    }
    return CsvImportResult(rows, errors)
}

/**
 * Merges imported rows into existing rows. An imported row replaces an existing row with the
 * same date; other dates are added. The result is sorted by date ascending. If a date appears
 * more than once, the later row wins.
 */
fun mergeImported(existing: List<CsvDailyRow>, imported: List<CsvDailyRow>): List<CsvDailyRow> {
    val byDate = TreeMap<String, CsvDailyRow>()
    for (row in existing) byDate[row.dateIso] = row
    for (row in imported) byDate[row.dateIso] = row
    return byDate.values.toList()
}

private class CsvRecord(val line: Int, val fields: List<String>, val error: String? = null)

/** Splits RFC 4180 text into records, tracking the physical line where each record starts. */
private fun splitCsvRecords(text: String): List<CsvRecord> {
    val records = mutableListOf<CsvRecord>()
    val fields = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var quotedField = false
    var line = 1
    var recordLine = 1
    var hasContent = false
    var i = 0

    fun endField() {
        fields += field.toString()
        field.clear()
        quotedField = false
    }

    fun endRecord() {
        endField()
        if (hasContent) records += CsvRecord(recordLine, fields.toList())
        fields.clear()
        hasContent = false
    }

    while (i < text.length) {
        val c = text[i]
        if (inQuotes) {
            when {
                c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' -> inQuotes = false
                else -> {
                    if (c == '\n') line++
                    field.append(c)
                }
            }
        } else {
            when (c) {
                '"' -> {
                    if (field.isEmpty() && !quotedField) {
                        inQuotes = true
                        quotedField = true
                    } else {
                        field.append(c)
                    }
                    hasContent = true
                }
                ',' -> {
                    endField()
                    hasContent = true
                }
                '\r', '\n' -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    endRecord()
                    line++
                    recordLine = line
                }
                else -> {
                    field.append(c)
                    hasContent = true
                }
            }
        }
        i++
    }

    if (inQuotes) {
        records += CsvRecord(recordLine, emptyList(), "unterminated quoted field")
    } else if (hasContent || field.isNotEmpty() || fields.isNotEmpty()) {
        endRecord()
    }
    return records
}

/** Returns (row, null) on success or (null, reason) when the row is invalid. */
private fun parseDataRow(fields: List<String>): Pair<CsvDailyRow?, String?> {
    if (fields.size != IMPORT_HEADER.size) {
        return null to "expected ${IMPORT_HEADER.size} fields, found ${fields.size}"
    }
    val date = fields[0]
    if (!IMPORT_DATE_REGEX.matches(date)) {
        return null to "invalid date '$date' (expected yyyy-MM-dd)"
    }
    try {
        LocalDate.parse(date, IMPORT_DATE_FORMAT)
    } catch (e: DateTimeParseException) {
        return null to "invalid date '$date' (expected yyyy-MM-dd)"
    }
    val steps = fields[1]
    if (!IMPORT_STEPS_REGEX.matches(steps)) {
        return null to "invalid steps '$steps' (expected integer >= 0)"
    }
    val stepsInt = steps.toIntOrNull() ?: return null to "steps '$steps' is too large"

    val numbers = ArrayList<Double>(5)
    for (index in 2..6) {
        val raw = fields[index]
        val value = raw.toDoubleOrNull()
            ?: return null to "invalid number '$raw' in ${IMPORT_HEADER[index]}"
        if (!value.isFinite() || value < 0.0) {
            return null to "number '$raw' in ${IMPORT_HEADER[index]} must be finite and >= 0"
        }
        numbers += value
    }
    return CsvDailyRow(
        dateIso = date,
        steps = stepsInt,
        distanceM = numbers[0],
        bmrKcal = numbers[1],
        rmrKcal = numbers[2],
        activeKcal = numbers[3],
        totalKcal = numbers[4],
    ) to null
}
