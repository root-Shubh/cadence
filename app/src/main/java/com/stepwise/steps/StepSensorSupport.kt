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

/**
 * True when ACTIVITY_RECOGNITION is granted. Below Android 10 it is granted by default.
 */
fun hasActivityRecognition(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED
}
