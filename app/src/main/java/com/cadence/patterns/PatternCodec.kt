package com.cadence.patterns

import com.cadence.gps.RoutePoint

/** One kept route point as stored: raw fix plus its smoothed position. */
data class RoutePointRecord(
    val timeMillis: Long,
    val accuracyM: Float,
    val lat: Double,
    val lon: Double,
    val smoothLat: Double,
    val smoothLon: Double,
)

/** One local day: every step time in epoch millis, and the kept route points. */
data class DayPatterns(
    val date: String,
    val stepMillis: List<Long> = emptyList(),
    val points: List<RoutePointRecord> = emptyList(),
) {
    fun withSteps(more: List<Long>): DayPatterns = copy(stepMillis = stepMillis + more)
    fun withPoint(point: RoutePointRecord): DayPatterns = copy(points = points + point)
}

/**
 * JSON Lines codec for one day file. Each line is a standalone JSON object:
 * - header: {"type":"day","version":1,"date":"yyyy-MM-dd"}
 * - steps:  {"type":"steps","t":[epochMillis,...]} (batches, up to [STEPS_PER_LINE] each)
 * - point:  {"type":"point","t":...,"acc":...,"lat":...,"lon":...,"sLat":...,"sLon":...}
 * Written by this codec only, so the decoder reads exactly this shape and skips anything else.
 */
object PatternCodec {

    const val VERSION = 1
    const val STEPS_PER_LINE = 500

    private val dateRe = Regex("\"date\":\"([0-9-]+)\"")
    private val stepsRe = Regex("\"t\":\\[([0-9,\\s-]*)\\]")
    private val timeRe = Regex("\"t\":(-?\\d+)")
    private val accRe = Regex("\"acc\":([-0-9.Ee+]+)")
    private val latRe = Regex("\"lat\":([-0-9.Ee+]+)")
    private val lonRe = Regex("\"lon\":([-0-9.Ee+]+)")
    private val sLatRe = Regex("\"sLat\":([-0-9.Ee+]+)")
    private val sLonRe = Regex("\"sLon\":([-0-9.Ee+]+)")

    fun encode(day: DayPatterns): String {
        val out = StringBuilder()
        out.append("{\"type\":\"day\",\"version\":").append(VERSION)
            .append(",\"date\":\"").append(day.date).append("\"}\n")
        day.stepMillis.chunked(STEPS_PER_LINE).forEach { chunk ->
            out.append("{\"type\":\"steps\",\"t\":[")
            chunk.forEachIndexed { i, t -> if (i > 0) out.append(','); out.append(t) }
            out.append("]}\n")
        }
        day.points.forEach { p ->
            out.append("{\"type\":\"point\",\"t\":").append(p.timeMillis)
                .append(",\"acc\":").append(p.accuracyM.toDouble())
                .append(",\"lat\":").append(p.lat)
                .append(",\"lon\":").append(p.lon)
                .append(",\"sLat\":").append(p.smoothLat)
                .append(",\"sLon\":").append(p.smoothLon)
                .append("}\n")
        }
        return out.toString()
    }

    /** Returns null when there is no valid header. Unknown or damaged lines are skipped. */
    fun decode(text: String): DayPatterns? {
        var date: String? = null
        val steps = ArrayList<Long>()
        val points = ArrayList<RoutePointRecord>()
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            when {
                trimmed.contains("\"type\":\"day\"") ->
                    date = dateRe.find(trimmed)?.groupValues?.get(1) ?: date
                trimmed.contains("\"type\":\"steps\"") ->
                    stepsRe.find(trimmed)?.groupValues?.get(1)?.split(',')
                        ?.mapNotNull { it.trim().toLongOrNull() }?.let { steps.addAll(it) }
                trimmed.contains("\"type\":\"point\"") -> {
                    val record = parsePoint(trimmed)
                    if (record != null) points.add(record)
                }
            }
        }
        val d = date ?: return null
        return DayPatterns(d, steps, points)
    }

    private fun parsePoint(line: String): RoutePointRecord? {
        fun num(re: Regex): Double? = re.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
        val t = timeRe.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        return RoutePointRecord(
            timeMillis = t,
            accuracyM = (num(accRe) ?: return null).toFloat(),
            lat = num(latRe) ?: return null,
            lon = num(lonRe) ?: return null,
            smoothLat = num(sLatRe) ?: return null,
            smoothLon = num(sLonRe) ?: return null,
        )
    }
}

/** Raw and smoothed positions for one accepted fix, as stored. */
fun RoutePoint.toRecord(): RoutePointRecord = RoutePointRecord(
    timeMillis = raw.timeMillis,
    accuracyM = raw.accuracyM,
    lat = raw.latitude,
    lon = raw.longitude,
    smoothLat = smoothLat,
    smoothLon = smoothLon,
)
