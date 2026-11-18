package com.stepwise.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.stepwise.gps.GpsDistance
import com.stepwise.gps.GpsFix
import com.stepwise.gps.StrideResult
import com.stepwise.gps.strideFromWalk
import kotlinx.coroutines.delay

@Composable
fun CalibrateScreen(
    saveStride: (Double) -> Unit,
    currentSteps: () -> Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locationManager = remember {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    val walk = remember { WalkHolder() }

    var walking by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var distanceM by remember { mutableDoubleStateOf(0.0) }
    var startSteps by remember { mutableIntStateOf(0) }
    var liveSteps by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    val listener = remember {
        object : LocationListener {
            override fun onLocationChanged(location: Location) {
                distanceM = walk.distance.addFix(
                    GpsFix(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyM = location.accuracy,
                        timeMillis = location.time,
                    ),
                )
            }

            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        }
    }

    // Live step count while walking.
    LaunchedEffect(walking) {
        while (walking) {
            liveSteps = currentSteps() - startSteps
            delay(1000)
        }
    }

    // GPS listener is registered only while walking and removed on Stop or when leaving the screen.
    DisposableEffect(walking) {
        if (walking) startGps(locationManager, listener)
        onDispose { locationManager.removeUpdates(listener) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            permissionDenied = false
            walk.distance = GpsDistance()
            distanceM = 0.0
            startSteps = currentSteps()
            liveSteps = 0
            message = null
            walking = true
        } else {
            permissionDenied = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Calibrate stride", style = MaterialTheme.typography.titleLarge)
        Text(
            "Walk a measured route of at least 50 m and 50 steps, then press Stop. " +
                "Your stride length is calculated from the distance and steps.",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (permissionDenied) {
            Text(
                "Location permission was denied. Allow location for Stepwise in system settings to calibrate.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Text(
            "Distance: ${"%.0f".format(distanceM)} m",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text("Steps during walk: $liveSteps", style = MaterialTheme.typography.headlineSmall)

        if (walking) {
            OutlinedButton(
                onClick = {
                    walking = false
                    val steps = currentSteps() - startSteps
                    when (val result = strideFromWalk(distanceM, steps)) {
                        is StrideResult.Success -> {
                            saveStride(result.strideCm)
                            message = "Stride saved: ${"%.1f".format(result.strideCm)} cm"
                        }
                        is StrideResult.Rejected -> message = "Not saved: ${result.reason}"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Stop")
            }
        } else {
            Button(
                onClick = {
                    message = null
                    if (hasLocationPermission(context)) {
                        walk.distance = GpsDistance()
                        distanceM = 0.0
                        startSteps = currentSteps()
                        liveSteps = 0
                        walking = true
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start")
            }
        }

        message?.let { Text(it, style = MaterialTheme.typography.titleMedium) }

        Text(
            "GPS works offline. No location or step data is sent anywhere.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/** Holds the distance tracker for the current walk; a new one is created per walk. */
private class WalkHolder {
    var distance = GpsDistance()
}

/** Only called after location permission has been granted. */
@SuppressLint("MissingPermission")
private fun startGps(locationManager: LocationManager, listener: LocationListener) {
    locationManager.requestLocationUpdates(
        LocationManager.GPS_PROVIDER,
        1000L,
        0f,
        listener,
    )
}
