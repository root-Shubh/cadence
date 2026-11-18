package com.cadence.patterns

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * The direction the user walks, for the walk drawing. No GPS.
 *
 * Prefers TYPE_GAME_ROTATION_VECTOR (gyroscope and accelerometer): its north is arbitrary, but it
 * is not bent by steel and wiring indoors, so shapes come out true. Falls back to
 * TYPE_ROTATION_VECTOR (adds the compass). [heading] is the latest [forwardHeading], or null
 * before the first reading or while the phone is held so that forward cannot be told.
 */
class HeadingSource(context: Context) : SensorEventListener {

    private val sensorManager: SensorManager? =
        context.applicationContext.getSystemService(SensorManager::class.java)
    private val matrix = FloatArray(9)
    private var registered = false

    @Volatile
    var heading: Double? = null
        private set

    /** Name of the sensor in use, for the debug log; null when none could be registered. */
    var sensorName: String? = null
        private set

    /** Registers the listener. Returns true when a rotation sensor is in use. */
    fun start(): Boolean {
        if (registered) return true
        val manager = sensorManager ?: return false
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: return false
        registered = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        sensorName = if (registered) sensor.name else null
        return registered
    }

    fun stop() {
        if (!registered) return
        sensorManager?.unregisterListener(this)
        registered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val type = event.sensor.type
        if (type != Sensor.TYPE_GAME_ROTATION_VECTOR && type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        // Keeps the last good heading while the phone is briefly held edge-on.
        forwardHeading(matrix)?.let { heading = it }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
