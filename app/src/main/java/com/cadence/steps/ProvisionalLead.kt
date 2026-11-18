package com.cadence.steps

/**
 * No accelerometer step for this long is a break in movement: the user stood still. After a break
 * the accelerometer shows steps again, so the next walk moves the count from its first step.
 */
const val MOVEMENT_BREAK_MS: Long = 3_000L

/**
 * No hardware detector step for this long while the user keeps moving means the sensor has gone
 * silent (it rejects some walks, for example with the phone held in the hand). The accelerometer
 * then shows steps again. Measured on a Galaxy S24 Ultra: while walking the detector reports every
 * 0.62 s (median), with the longest gap 1.87 s, so 4 s is twice the longest real gap.
 */
const val SENSOR_SILENT_MS: Long = 4_000L

/**
 * How long provisional steps wait for the hardware counter after the last one. The counter
 * confirms steps while the walk goes on, so a lead it has not confirmed 10 s after the walk
 * stopped was a burst it rejected, and is dropped.
 */
const val LEAD_EXPIRY_MS: Long = 10_000L

/**
 * Most accelerometer steps shown ahead of the hardware counter on a phone with a step detector.
 * The phone holds back the first steps of a walk and releases them in one batch (12 and 13 steps
 * measured on a Galaxy S24 Ultra); 15 covers that warm-up while keeping any false count from
 * handling the phone to at most 15.
 */
const val WARMUP_LEAD_CAP: Long = 15L

/**
 * Steps shown before the hardware step counter reports them. Pure Kotlin, not thread-safe: the
 * service calls it under its count lock.
 *
 * - Detector steps always add to the lead, and mark the sensor active; the counter follows them
 *   within a moment.
 * - While the sensor is active, accelerometer steps are ignored, so handling the phone at the end
 *   of a walk adds nothing. The accelerometer counts again only after a break in movement
 *   ([movementBreakMs] with no accelerometer step) or when the detector has been silent for
 *   [sensorSilentMs] while the user keeps moving. Before the first detector step (the warm-up,
 *   when the phone holds steps back) it counts.
 * - A counter reading takes the lead ([takeForCounter]): the reading already holds those steps.
 * - With [expiryMs] set, a lead the counter has not confirmed within that time is dropped.
 *   Pass Long.MAX_VALUE on phones without a detector, where the counter can stay silent for long.
 */
class ProvisionalLead(
    private val expiryMs: Long = LEAD_EXPIRY_MS,
    private val movementBreakMs: Long = MOVEMENT_BREAK_MS,
    private val sensorSilentMs: Long = SENSOR_SILENT_MS,
    private val maxAccelLead: Long = Long.MAX_VALUE,
) {
    private var steps = 0L
    private var lastLeadMillis = 0L
    private var lastDetectorMillis: Long? = null
    private var lastAccelMillis: Long? = null
    private var sensorActive = false

    /** One hardware detector step. */
    fun onDetectorStep(nowMillis: Long) {
        steps++
        lastLeadMillis = nowMillis
        lastDetectorMillis = nowMillis
        sensorActive = true
    }

    /** One accelerometer step. Returns true when it was counted as provisional. */
    fun onAccelStep(nowMillis: Long): Boolean {
        val previousAccel = lastAccelMillis
        lastAccelMillis = nowMillis
        if (sensorActive) {
            val brokeMovement = previousAccel == null || nowMillis - previousAccel >= movementBreakMs
            val lastDetector = lastDetectorMillis
            val sensorSilent = lastDetector == null || nowMillis - lastDetector >= sensorSilentMs
            if (!brokeMovement && !sensorSilent) return false
            sensorActive = false
        }
        // An expired lead is gone: the next step starts a new one.
        if (steps > 0 && current(nowMillis) == 0L) steps = 0L
        if (steps >= maxAccelLead) {
            // Still walking: the capped lead stays until the walk stops, instead of flickering.
            lastLeadMillis = nowMillis
            return false
        }
        steps++
        lastLeadMillis = nowMillis
        return true
    }

    /** True while detector steps are the visual source and the accelerometer is ignored. */
    fun isSensorActive(): Boolean = sensorActive

    /** The lead to show now: zero once it has waited longer than [expiryMs] for the counter. */
    fun current(nowMillis: Long): Long =
        if (steps > 0 && expiryMs != Long.MAX_VALUE && nowMillis - lastLeadMillis > expiryMs) 0L else steps

    /** Called on each counter reading: returns the lead to credit and starts a new one. */
    fun takeForCounter(nowMillis: Long): Long {
        val lead = current(nowMillis)
        steps = 0L
        return lead
    }

    /** Forgets everything, for example on a new day or when counting starts. */
    fun reset() {
        steps = 0L
        lastLeadMillis = 0L
        lastDetectorMillis = null
        lastAccelMillis = null
        sensorActive = false
    }
}
