package com.cadence.steps

import kotlin.math.max
import kotlin.math.min

/**
 * The most steps the accelerometer may lead the hardware step counter by. Accelerometer steps
 * that the counter has not reported yet are shown as a provisional lead, capped at this value,
 * so a burst of handling or shaking that the counter never sees cannot inflate the total.
 */
const val ACCEL_LEAD_CAP = 10

/**
 * Today's displayed total: the hardware counter total plus a provisional lead. The lead is the
 * accelerometer steps not yet matched by the counter, capped at [cap]. When the counter reports
 * those steps the lead drops to zero, so the display and the saved total converge. Pure Kotlin,
 * no Android dependencies.
 *
 * [accelSteps] is the number of accelerometer steps the counter has not yet reported. Values
 * below zero count as zero, and the result is clamped to the Int range.
 */
fun displayTotal(counterTotal: Int, accelSteps: Int, cap: Int = ACCEL_LEAD_CAP): Int {
    val lead = min(max(accelSteps, 0), max(cap, 0))
    val total = counterTotal.toLong() + lead
    return total.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

/**
 * The total to store for today. It never goes below the value already stored, so a late or
 * out-of-order write cannot lower the stored steps.
 */
fun mergeStored(previousStored: Int, candidate: Int): Int = max(previousStored, candidate)

/** Most the accelerometer lead may ever be, however long the hardware counter stays silent. */
const val ACCEL_LEAD_CAP_MAX = 120

/**
 * The lead cap for the current moment. The hardware counter can stay silent for many seconds
 * while the user walks, so a fixed cap of [ACCEL_LEAD_CAP] freezes the display after a few steps.
 * The cap grows with the silence: [ACCEL_LEAD_CAP] plus [maxStepsPerSecond] for each second since
 * the counter last reported, up to [ACCEL_LEAD_CAP_MAX]. A shake still cannot count faster than the
 * detector's step rate. Pure Kotlin, no Android dependencies.
 */
fun accelLeadCap(
    nowMillis: Long,
    lastCounterMillis: Long,
    maxStepsPerSecond: Double,
): Int {
    val silentSeconds = max(0L, nowMillis - lastCounterMillis) / 1000.0
    val grown = ACCEL_LEAD_CAP + kotlin.math.ceil(silentSeconds * maxStepsPerSecond).toInt()
    return min(grown, ACCEL_LEAD_CAP_MAX)
}
