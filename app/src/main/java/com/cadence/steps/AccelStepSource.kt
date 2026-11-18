package com.cadence.steps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Software step source for devices without a hardware step counter. It reads the accelerometer,
 * feeds the vector length to [StepDetector], and calls [onStep] with the sample time for each step.
 *
 * If the device has no accelerometer, [start] does nothing.
 */
class AccelStepSource(
    context: Context,
    private val onStep: (Long) -> Unit,
) : SensorEventListener {

    private val sensorManager: SensorManager? =
        context.applicationContext.getSystemService(SensorManager::class.java)
    private val detector = StepDetector()
    private var registered = false

    /** Registers the listener. Safe to call more than once. */
    fun start() {
        if (registered) return
        val manager = sensorManager ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        registered = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    /** Unregisters the listener. Safe to call when not started. */
    fun stop() {
        if (!registered) return
        sensorManager?.unregisterListener(this)
        registered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val timeMillis = event.timestamp / NANOS_PER_MILLI
        if (detector.onSample(magnitude, timeMillis) == 1) {
            onStep(timeMillis)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used: the detector does not depend on sensor accuracy.
    }

    private companion object {
        /** SensorEvent.timestamp is in nanoseconds. */
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
