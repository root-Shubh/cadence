package com.cadence.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cadence.patterns.PatternStore
import com.cadence.patterns.dayIsoOf
import com.cadence.patterns.toRecord
import com.cadence.steps.hasFineLocation
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the route card shows about the GPS fix. Satellite counts come from GnssStatus. */
data class GpsStatus(
    val off: Boolean = true,
    val satellitesInView: Int = 0,
    val satellitesUsed: Int = 0,
    val fixAccuracyM: Float? = null,
) {
    val label: String
        get() = when {
            off -> "GPS: off"
            fixAccuracyM != null -> "GPS: $satellitesUsed satellites, accuracy ${fixAccuracyM.toInt()} m"
            else -> "GPS: searching, $satellitesInView satellites"
        }
}

/**
 * Starts and stops GPS route recording for a Record session. It only maps the route; it never
 * touches step counting. Start does nothing unless location permission is granted and GPS is on,
 * so counting is never blocked by it. Logs use "CadenceRoute" (points) and "CadenceGps" (GNSS
 * and request lifecycle). Coordinates are never logged.
 */
object RouteRecording {

    private const val TAG = "CadenceRoute"
    private const val TAG_GPS = "CadenceGps"
    private const val GNSS_LOG_INTERVAL_MS = 5_000L

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val statusFlow = MutableStateFlow(GpsStatus())
    val status: StateFlow<GpsStatus> = statusFlow.asStateFlow()

    private var manager: LocationManager? = null
    private var listener: LocationListener? = null
    private var gnssCallback: GnssStatus.Callback? = null
    private var lastGnssLogMillis = 0L

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
        val enabled = locations.isProviderEnabled(LocationManager.GPS_PROVIDER)
        Log.d(TAG_GPS, "provider state: gps enabled=$enabled")
        if (!enabled) {
            Log.d(TAG, "route recording off: GPS is off")
            statusFlow.value = GpsStatus(off = true)
            return
        }

        val store = PatternStore(app.filesDir)
        val zone = ZoneId.systemDefault()
        val recorder = RouteRecorder { point ->
            val date = dayIsoOf(point.raw.timeMillis, zone)
            val record = point.toRecord()
            writeScope.launch { store.appendPoint(profileId, date, record) }
        }
        val fixListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // Coordinates are deliberately not logged.
                Log.d(TAG_GPS, "onLocationChanged accuracy=${location.accuracy} m time=${location.time}")
                statusFlow.value = statusFlow.value.copy(off = false, fixAccuracyM = location.accuracy)
                val fix = GpsFix(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyM = location.accuracy,
                    timeMillis = location.time,
                )
                if (recorder.offer(fix)) {
                    Log.d(TAG, "kept point #${recorder.keptCount} accuracy=${fix.accuracyM} m time=${fix.timeMillis}")
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        }

        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var used = 0
                for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
                val total = status.satelliteCount
                statusFlow.value = statusFlow.value.copy(
                    off = false,
                    satellitesInView = total,
                    satellitesUsed = used,
                )
                val now = System.currentTimeMillis()
                if (now - lastGnssLogMillis >= GNSS_LOG_INTERVAL_MS) {
                    lastGnssLogMillis = now
                    Log.d(TAG_GPS, "gnss: satellites in view=$total used in fix=$used")
                }
            }

            override fun onStarted() { Log.d(TAG_GPS, "gnss: started") }
            override fun onStopped() { Log.d(TAG_GPS, "gnss: stopped") }
        }

        try {
            locations.registerGnssStatusCallback(callback, mainHandler)
            locations.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,
                0f,
                fixListener,
                Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            Log.d(TAG, "route recording off: ${e.javaClass.simpleName}")
            locations.unregisterGnssStatusCallback(callback)
            return
        }
        manager = locations
        listener = fixListener
        gnssCallback = callback
        lastGnssLogMillis = 0L
        statusFlow.value = GpsStatus(off = false)
        Log.d(TAG_GPS, "request registered: GPS_PROVIDER 0 ms, 0 m, listener=${System.identityHashCode(fixListener)}")
        Log.d(TAG, "route recording started")
    }

    /** Removes the location updates and the GNSS callback. Safe to call when not running. */
    @Synchronized
    fun stop() {
        val current = listener ?: return
        manager?.removeUpdates(current)
        gnssCallback?.let { manager?.unregisterGnssStatusCallback(it) }
        Log.d(TAG_GPS, "request removed: listener=${System.identityHashCode(current)}")
        manager = null
        listener = null
        gnssCallback = null
        statusFlow.value = GpsStatus(off = true)
        Log.d(TAG, "route recording stopped")
    }
}
