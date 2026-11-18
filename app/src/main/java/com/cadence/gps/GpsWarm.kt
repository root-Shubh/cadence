package com.cadence.gps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the GPS is doing right now, shown on screen. */
data class GnssState(
    val gpsOn: Boolean = false,
    val inView: Int = 0,
    val used: Int = 0,
    val accuracyM: Float? = null,
    val lastFixMillis: Long? = null,
)

/**
 * Keeps GPS running while the app is in the foreground, so a lock is ready before any walk or
 * route starts. Started on resume and stopped on pause. Steps never depend on this.
 */
object GpsWarm {

    const val TAG: String = "CadenceGps"

    private val _state = MutableStateFlow(GnssState())
    val state: StateFlow<GnssState> = _state.asStateFlow()

    private var manager: LocationManager? = null
    private var active = false
    private val handler = Handler(Looper.getMainLooper())

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _state.value = _state.value.copy(
                accuracyM = location.accuracy,
                lastFixMillis = System.currentTimeMillis(),
            )
            Log.d(TAG, "warm fix acc=${"%.1f".format(location.accuracy)}m")
        }

        override fun onProviderEnabled(provider: String) = refreshGpsOn()

        override fun onProviderDisabled(provider: String) = refreshGpsOn()

        @Deprecated("Deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    }

    private val gnss = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
            _state.value = _state.value.copy(inView = status.satelliteCount, used = used)
        }
    }

    /** Starts GPS if the app may use location. Safe to call again. */
    fun start(context: Context) {
        val app = context.applicationContext
        val lm = app.getSystemService(LocationManager::class.java) ?: return
        manager = lm
        if (active) {
            refreshGpsOn()
            return
        }
        val fine = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine) {
            refreshGpsOn()
            return
        }
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
            lm.registerGnssStatusCallback(gnss, handler)
            active = true
            Log.d(TAG, "GPS warm: started")
        } catch (e: SecurityException) {
            Log.w(TAG, "GPS warm: not started", e)
        }
        refreshGpsOn()
    }

    /** Called when recording stops: GPS is released unless the app is open. */
    fun onRecordingStopped() {
        if (!appOpen) stop()
    }

    private var appOpen = false

    /** Tracks whether the app is in the foreground. */
    fun setAppOpen(open: Boolean) {
        appOpen = open
    }

    /** Stops GPS. Called when the app goes to the background and recording is off. */
    fun stop() {
        val lm = manager ?: return
        if (active) {
            try {
                lm.removeUpdates(listener)
                lm.unregisterGnssStatusCallback(gnss)
            } catch (e: SecurityException) {
                Log.w(TAG, "GPS warm: stop failed", e)
            }
            active = false
            Log.d(TAG, "GPS warm: stopped")
        }
        _state.value = GnssState(gpsOn = refreshedGpsOn())
    }

    private fun refreshedGpsOn(): Boolean = manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true

    private fun refreshGpsOn() {
        _state.value = _state.value.copy(gpsOn = refreshedGpsOn())
    }
}
