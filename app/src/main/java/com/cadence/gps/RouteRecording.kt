package com.cadence.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import com.cadence.data.RoutePointEntity
import com.cadence.data.StepDatabase
import com.cadence.steps.hasFineLocation
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Starts and stops GPS route recording for a Record session. It only maps the route; it never
 * touches step counting. Start does nothing unless location permission is granted and GPS is on,
 * so counting is never blocked by it.
 */
object RouteRecording {

    private const val TAG = "CadenceRoute"

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var manager: LocationManager? = null
    private var listener: LocationListener? = null

    @Synchronized
    fun isActive(): Boolean = listener != null

    /** Call on the main thread. No-op when already running or when GPS or permission is missing. */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(context: Context, profileId: String) {
        if (listener != null) return
        val app = context.applicationContext
        if (!hasFineLocation(app)) {
            Log.d(TAG, "route recording off: location permission not granted")
            return
        }
        val locations = app.getSystemService(LocationManager::class.java) ?: return
        if (!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.d(TAG, "route recording off: GPS is off")
            return
        }

        val dao = StepDatabase.getInstance(app).routePointDao()
        val recorder = RouteRecorder { fix ->
            val entity = RoutePointEntity(
                profileId = profileId,
                dateIso = dateIsoOf(fix.timeMillis),
                timeMillis = fix.timeMillis,
                lat = fix.latitude,
                lon = fix.longitude,
                accuracyM = fix.accuracyM,
            )
            writeScope.launch { dao.insert(entity) }
        }
        val fixListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val fix = GpsFix(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyM = location.accuracy,
                    timeMillis = location.time,
                )
                // Coordinates are deliberately not logged.
                if (recorder.offer(fix)) {
                    Log.d(TAG, "kept point #${recorder.keptCount} accuracy=${fix.accuracyM} m time=${fix.timeMillis}")
                }
            }
        }

        try {
            locations.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,
                0f,
                fixListener,
                Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            Log.d(TAG, "route recording off: ${e.javaClass.simpleName}")
            return
        }
        manager = locations
        listener = fixListener
        Log.d(TAG, "route recording started")
    }

    /** Removes the location updates. Safe to call when not running. */
    @Synchronized
    fun stop() {
        val current = listener ?: return
        manager?.removeUpdates(current)
        manager = null
        listener = null
        Log.d(TAG, "route recording stopped")
    }

    private fun dateIsoOf(timeMillis: Long): String =
        Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}
