package com.stepwise.steps

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
