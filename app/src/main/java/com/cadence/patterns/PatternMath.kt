package com.cadence.patterns

import java.time.Instant
import java.time.ZoneId

/**
 * Pure math for the daily pattern. Step times are the source of truth; every chart bucket is
 * derived from them, so totals match at every zoom level. Local time zone, local midnight day
 * boundaries. No Android types.
 */

/** Bucket widths in minutes, finest last. A bucket width always divides 60. */
val BUCKET_MINUTES: IntArray = intArrayOf(60, 30, 15, 5, 1)

/** Zoom factor range. 1 shows hourly buckets; higher zoom gives finer buckets. */
const val MIN_ZOOM: Double = 1.0
const val MAX_ZOOM: Double = 60.0

/** Local date (yyyy-MM-dd) of an epoch-millis instant. */
fun dayIsoOf(millis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()

/** Bucket width for a zoom factor: the largest width no wider than 60 / zoom minutes. */
fun bucketMinutesForZoom(zoom: Double): Int {
    val z = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    val ideal = 60.0 / z
    return BUCKET_MINUTES.firstOrNull { it <= ideal } ?: BUCKET_MINUTES.last()
}

/**
 * Steps per bucket for [date] (yyyy-MM-dd, local). Steps on other days are ignored. The sum of
 * the result is the number of step times on that day, at any bucket width.
 */
fun bucketCounts(
    stepMillis: List<Long>,
    date: String,
    zone: ZoneId,
    bucketMinutes: Int,
): LongArray {
    require(bucketMinutes > 0 && 1440 % bucketMinutes == 0) { "bucket width must divide a day" }
    val out = LongArray(1440 / bucketMinutes)
    for (millis in stepMillis) {
        val local = Instant.ofEpochMilli(millis).atZone(zone)
        if (local.toLocalDate().toString() != date) continue
        val minuteOfDay = local.hour * 60 + local.minute
        out[minuteOfDay / bucketMinutes]++
    }
    return out
}

/** Index of the largest bucket, or -1 when there are no steps. */
fun peakIndex(buckets: LongArray): Int {
    var best = -1
    var bestValue = 0L
    buckets.forEachIndexed { i, v ->
        if (v > bestValue) {
            bestValue = v
            best = i
        }
    }
    return best
}

/** Number of hours with at least one step, from hourly buckets. */
fun activeHours(hourly: LongArray): Int = hourly.count { it > 0 }
