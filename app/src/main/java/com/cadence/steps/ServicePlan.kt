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
