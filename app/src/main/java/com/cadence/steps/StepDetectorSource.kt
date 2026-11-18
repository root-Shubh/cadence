package com.cadence.steps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Step source backed by the hardware TYPE_STEP_DETECTOR sensor. It reports one event per
 * detected step, with much lower latency than the batched TYPE_STEP_COUNTER. [onStep] is called
 * on the thread that delivers sensor events (the main thread for this service) with the event
 * timestamp in nanoseconds.
 *
 * If the device has no step detector, [start] returns false and nothing is registered.
 */
class StepDetectorSource(
    context: Context,
    private val onStep: (timestampNanos: Long) -> Unit,
) : SensorEventListener {

    private val sensorManager: SensorManager? =
        context.applicationContext.getSystemService(SensorManager::class.java)
    private var registered = false

    /** Registers the listener at the fastest delay the device accepts. Returns true when registered. */
    fun start(): Boolean {
        if (registered) return true
        val manager = sensorManager ?: return false
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) ?: return false
        // Maximum report latency 0: each step is delivered as it happens, without batching.
        registered = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST, 0)
        return registered
    }

    /** Unregisters the listener. Safe to call when not started. */
    fun stop() {
        if (!registered) return
        sensorManager?.unregisterListener(this)
        registered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_DETECTOR) return
        if (!isDetectedStep(event.values[0])) return
        onStep(event.timestamp)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used: each detected step counts the same.
    }
}

/** True when the device exposes a TYPE_STEP_DETECTOR sensor. */
fun hasStepDetector(context: Context): Boolean {
    val manager = context.getSystemService(SensorManager::class.java)
    return manager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
}
