package com.cadence.debug

import android.hardware.Sensor
import android.hardware.SensorManager

/** What the device reports for one step-related sensor. [present] false means no such sensor. */
data class SensorInfo(
    val label: String,
    val type: Int,
    val present: Boolean,
    val name: String? = null,
    val vendor: String? = null,
    val version: Int? = null,
    val minDelayMicros: Int? = null,
    val wakeUp: Boolean? = null,
)

/** The sensors the step counter can use, in the order the fallback chain tries them. */
val STEP_SENSOR_TYPES: List<Pair<String, Int>> = listOf(
    "TYPE_STEP_DETECTOR" to Sensor.TYPE_STEP_DETECTOR,
    "TYPE_STEP_COUNTER" to Sensor.TYPE_STEP_COUNTER,
    "TYPE_ACCELEROMETER" to Sensor.TYPE_ACCELEROMETER,
)

/** Reads the default sensor of each step-related type. Call on the main thread or a worker. */
fun readStepSensors(manager: SensorManager?): List<SensorInfo> = STEP_SENSOR_TYPES.map { (label, type) ->
    val sensor = manager?.getDefaultSensor(type)
    if (sensor == null) {
        SensorInfo(label = label, type = type, present = false)
    } else {
        SensorInfo(
            label = label,
            type = type,
            present = true,
            name = sensor.name,
            vendor = sensor.vendor,
            version = sensor.version,
            minDelayMicros = sensor.minDelay,
            wakeUp = sensor.isWakeUpSensor,
        )
    }
}

/**
 * Lines for the Debug panel. An absent sensor is shown as a hardware fact, so a missing step
 * counter can be told apart from a software fault.
 */
fun describeSensors(infos: List<SensorInfo>): List<String> = infos.map { info ->
    if (!info.present) {
        "${info.label}: not present on this device"
    } else {
        val minDelay = info.minDelayMicros?.let { "${it / 1000} ms min delay" } ?: "min delay unknown"
        val wake = if (info.wakeUp == true) "wake-up" else "non-wake-up"
        "${info.label}: ${info.name} (${info.vendor}, v${info.version}), $minDelay, $wake"
    }
}
