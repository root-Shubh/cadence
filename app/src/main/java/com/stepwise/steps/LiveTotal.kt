package com.stepwise.steps

import kotlin.math.max

/**
 * Today's total from the two step sources: the step counter based total and the accelerometer
 * based total. Taking the larger one means a step seen by both sources is counted once, and the
 * total moves as soon as either source reports a step. Pure Kotlin, no Android dependencies.
 */
fun liveTotal(counterTotal: Int, accelTotal: Int): Int = max(counterTotal, accelTotal)

/**
 * The total to store for today. It never goes below the value already stored, so a late or
 * out-of-order write cannot lower the stored steps.
 */
fun mergeStored(previousStored: Int, candidate: Int): Int = max(previousStored, candidate)
