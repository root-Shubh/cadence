package com.stepwise.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Whether the device exposes a hardware step counter. */
sealed class StepSensorState {
    object Available : StepSensorState()
    object NoSensor : StepSensorState()
}

/** Checks for a TYPE_STEP_COUNTER sensor. */
fun stepSensorState(context: Context): StepSensorState {
    val manager = context.getSystemService(SensorManager::class.java)
    val sensor = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    return if (sensor != null) StepSensorState.Available else StepSensorState.NoSensor
}

/** Whether the device exposes an accelerometer, used by the software fallback. */
fun hasAccelerometer(context: Context): Boolean {
    val manager = context.getSystemService(SensorManager::class.java)
    return manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
}

/** How steps can be counted on this device: hardware counter, accelerometer, or not at all. */
sealed class StepTrackingSource {
    object StepCounter : StepTrackingSource()
    object Accelerometer : StepTrackingSource()
    object None : StepTrackingSource()
}

/** The extended state: a step counter wins, then the accelerometer fallback, else none. */
fun stepTrackingSource(context: Context): StepTrackingSource = when {
    stepSensorState(context) is StepSensorState.Available -> StepTrackingSource.StepCounter
    hasAccelerometer(context) -> StepTrackingSource.Accelerometer
    else -> StepTrackingSource.None
}

/**
 * True when ACTIVITY_RECOGNITION is granted. Below Android 10 it is granted by default.
 */
fun hasActivityRecognition(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED
}
