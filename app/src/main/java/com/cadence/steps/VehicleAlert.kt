package com.cadence.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Notification id for the vehicle alert. Separate from the counting notification. */
const val VEHICLE_NOTIFICATION_ID: Int = 1302

const val VEHICLE_ALERT_TITLE: String = "Are you cheating? 🚗"
const val VEHICLE_ALERT_TEXT: String =
    "Looks like you're moving at car or bike speed. Counting is paused and the steps counted during the ride have been removed. It resumes by itself when you walk again."

/** Text of the ongoing notification shown while a vehicle pause runs. */
const val VEHICLE_PAUSED_TEXT: String =
    "Vehicle speed detected. Counting resumes by itself when you walk again."

/**
 * Process-wide vehicle alert. The step service raises it when a vehicle is detected and pauses
 * counting. The UI shows the "Are you cheating?" dialog while [pending] is true and the app is
 * open; otherwise the service posts a notification.
 */
object VehicleAlert {

    private val _pending = MutableStateFlow(false)

    /** True until the user acknowledges the alert. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    @Volatile
    var appVisible: Boolean = false

    fun raise() {
        _pending.value = true
    }

    fun dismiss() {
        _pending.value = false
    }
}

/**
 * True when fine location is granted. The GPS provider only gives fixes with fine location,
 * so the vehicle check is off without it. Coarse location alone does not enable it.
 */
fun hasFineLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
