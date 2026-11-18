package com.stepwise.export

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.TreeMap

/** One day of recorded data, ready for CSV export. */
data class CsvDailyRow(
    val dateIso: String,
    val steps: Int,
    val distanceM: Double,
    val bmrKcal: Double,
    val rmrKcal: Double,
    val activeKcal: Double,
    val totalKcal: Double,
)

private const val CRLF = "\r\n"
private const val DAILY_HEADER = "date,steps,distance_m,bmr_kcal,rmr_kcal,active_kcal,total_kcal"
private val ISO_DATE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")

/**
 * Builds the daily CSV. Rows are sorted by date ascending; if the same date appears
 * more than once, the last occurrence wins. An empty list yields only the header line.
 */
fun buildDailyCsv(rows: List<CsvDailyRow>): String {
    val byDate = TreeMap<String, CsvDailyRow>()
    for (row in rows) {
        requireIsoDate(row.dateIso)
        byDate[row.dateIso] = row
    }
    val sb = StringBuilder()
    sb.append(DAILY_HEADER).append(CRLF)
    for (row in byDate.values) {
        sb.append(escapeCsv(row.dateIso)).append(',')
        sb.append(row.steps).append(',')
        sb.append(fixed2(row.distanceM)).append(',')
        sb.append(fixed2(row.bmrKcal)).append(',')
        sb.append(fixed2(row.rmrKcal)).append(',')
        sb.append(fixed2(row.activeKcal)).append(',')
        sb.append(fixed2(row.totalKcal)).append(CRLF)
    }
    return sb.toString()
}

/** Returns "stepwise-export-<todayIso>.csv". Throws IllegalArgumentException for an invalid date. */
fun csvFileName(todayIso: String): String {
    requireIsoDate(todayIso)
    return "stepwise-export-$todayIso.csv"
}

private fun requireIsoDate(value: String) {
    require(ISO_DATE_REGEX.matches(value)) { "Invalid date (expected yyyy-MM-dd): $value" }
    try {
        LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT))
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Invalid date (expected yyyy-MM-dd): $value", e)
    }
}

private fun fixed2(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

/** RFC 4180 escaping: quote fields containing comma, quote, CR or LF; double internal quotes. */
internal fun escapeCsv(field: String): String {
    val needsQuoting = field.any { it == ',' || it == '"' || it == '\r' || it == '\n' }
    return if (needsQuoting) "\"" + field.replace("\"", "\"\"") + "\"" else field
}
