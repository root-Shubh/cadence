package com.cadence.steps

/** What the step service does once it has called startForeground. */
enum class ServicePlan {
    /** Permission and sensor present: register the step counter and count. */
    COUNT,

    /** No step counter on this device: show a notice and stop. */
    NO_SENSOR,

    /** ACTIVITY_RECOGNITION not granted: show a notice and stop. */
    NO_PERMISSION,

    /**
     * No step counter, but an accelerometer exists: count steps in software with
     * [StepDetector] and show the "sensor-free" notification.
     */
    ACCELEROMETER,
}

/**
 * Decides the service plan. Permission is checked first because counting needs it even
 * when the sensor exists. The service must still call startForeground in every case, because
 * Android kills the app if startForegroundService is not followed by startForeground in time.
 */
fun servicePlan(
    hasPermission: Boolean,
    hasSensor: Boolean,
    hasAccelerometer: Boolean = false,
): ServicePlan = when {
    !hasPermission -> ServicePlan.NO_PERMISSION
    hasSensor -> ServicePlan.COUNT
    hasAccelerometer -> ServicePlan.ACCELEROMETER
    else -> ServicePlan.NO_SENSOR
}

/**
 * Whether the accelerometer runs beside the hardware step counter. It is a fallback only: with a
 * working step detector it stays off, because its provisional steps showed and then vanished
 * while the user stood still (STEPW-62).
 */
fun accelerometerNeeded(detectorRunning: Boolean, hasAccelerometer: Boolean): Boolean =
    hasAccelerometer && !detectorRunning

/** Step events logged one by one at the start of each session; the heartbeat covers the rest. */
const val STEP_EVENT_LOG_LIMIT: Long = 5L

/**
 * True for the first [STEP_EVENT_LOG_LIMIT] step events of a session ([eventNumber] counts from 1).
 * Logging every step filled the debug log in about 25 minutes (STEPW-61).
 */
fun shouldLogStepEvent(eventNumber: Long): Boolean = eventNumber in 1..STEP_EVENT_LOG_LIMIT
