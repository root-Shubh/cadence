package com.cadence.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cadence.gps.GpsDistance
import com.cadence.gps.GpsFix
import com.cadence.gps.StrideResult
import com.cadence.gps.strideFromWalk
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val walk = remember { WalkHolder() }
    val buttonSource = remember { MutableInteractionSource() }

    var walking by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var distanceM by remember { mutableDoubleStateOf(0.0) }
    var startSteps by remember { mutableIntStateOf(0) }
    var liveSteps by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    // True when the user asked to start and the walk waits for permission and GPS.
    var pendingStart by remember { mutableStateOf(false) }
    var showGpsDialog by remember { mutableStateOf(false) }
    // Bumped on every ON_RESUME (for example, back from location settings) to re-check GPS.
    var resumeCount by remember { mutableIntStateOf(0) }
    // True when GPS was switched off mid-walk: the walk is paused and resumes once GPS is back on.
    var gpsPaused by remember { mutableStateOf(false) }
    var pausedSteps by remember { mutableIntStateOf(0) }
    // Bumped on every location provider change while a walk runs or is paused, and on resume.
    var gpsEvents by remember { mutableIntStateOf(0) }

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

    // The single place that decides whether a walk can start: it needs location permission
    // and GPS. It runs straight from Start, so the GPS dialog shows on that tap, and again when
    // the permission answer, a resume or a GPS change arrives while a start is pending.
    fun tryStart() {
        if (!hasLocationPermission(context)) {
            pendingStart = false
            permissionDenied = true
            return
        }
        if (isGpsEnabled(locationManager)) {
            pendingStart = false
            showGpsDialog = false
            walk.distance = GpsDistance()
            distanceM = 0.0
            startSteps = currentSteps()
            liveSteps = 0
            message = null
            walking = true
        } else {
            pendingStart = true
            showGpsDialog = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        permissionDenied = !granted
        if (granted) tryStart()
    }

    // Location permission first, then the GPS check. Called after the notification answer.
    fun beginLocationStart() {
        if (hasLocationPermission(context)) {
            permissionDenied = false
            tryStart()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    // Asks for the notification permission before the walk starts (Android 13+).
    val requestNotifications = rememberNotificationRequest { beginLocationStart() }

    // Re-check a pending start when the user comes back from location settings.
    LaunchedEffect(resumeCount) {
        if (pendingStart) tryStart()
    }

    // Listen for location on/off changes while a walk runs or is paused.
    val watching = walking || gpsPaused
    DisposableEffect(watching) {
        if (!watching) return@DisposableEffect onDispose {}
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                gpsEvents++
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Pauses the walk when GPS turns off and resumes it when GPS comes back.
    LaunchedEffect(gpsEvents) {
        if (gpsEvents == 0) return@LaunchedEffect
        val enabled = isGpsEnabled(locationManager)
        if (walking && !enabled) {
            pausedSteps = currentSteps() - startSteps
            liveSteps = pausedSteps
            message = null
            walking = false
            gpsPaused = true
        } else if (gpsPaused && enabled) {
            gpsPaused = false
            startSteps = currentSteps() - pausedSteps
            walking = true
        } else if (pendingStart && enabled) {
            tryStart()
        }
    }

    // Re-check GPS when the user returns to this screen.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeCount++
                gpsEvents++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Reveal(index = 0, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Calibrate stride", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Walk a measured route of at least 50 m and 50 steps, then press Stop. " +
                        "Your stride length is calculated from the distance and steps.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Reveal(index = 1, modifier = Modifier.fillMaxWidth()) {
            PermissionsPanel()
        }

        if (permissionDenied) {
            Reveal(index = 1, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Location permission was denied. Allow location for Cadence in system settings to calibrate.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(
                        shape = MaterialTheme.shapes.extraLarge,
                        onClick = { startSettingsIntent(context, appSettingsIntent(context)) },
                    ) {
                        Text("Open app settings")
                    }
                }
            }
        }

        if (gpsPaused) {
            Reveal(index = 1, modifier = Modifier.fillMaxWidth()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "GPS is off. Turn location back on to keep measuring.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(
                            shape = MaterialTheme.shapes.extraLarge,
                            onClick = {
                                startSettingsIntent(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Turn on")
                        }
                    }
                }
            }
        }

        Reveal(index = 2, modifier = Modifier.fillMaxWidth()) {
            StatCard(
                label = "Distance",
                icon = Icons.Filled.Route,
                tone = Tone.TERTIARY,
                value = {
                    AnimatedCountText(
                        value = Math.round(distanceM).toInt(),
                        suffix = " m",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
            )
        }
        Reveal(index = 3, modifier = Modifier.fillMaxWidth()) {
            StatCard(
                label = "Steps during walk",
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                tone = Tone.PRIMARY,
                value = {
                    AnimatedCountText(
                        value = liveSteps,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
            )
        }

        if (!walking && message == null) {
            Reveal(index = 4, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Your stride is estimated from your height until you calibrate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Reveal(index = 5, modifier = Modifier.fillMaxWidth()) {
            if (walking) {
                OutlinedButton(
                    shape = MaterialTheme.shapes.extraLarge,
                    interactionSource = buttonSource,
                    onClick = {
                        walking = false
                        val steps = currentSteps() - startSteps
                        liveSteps = steps
                        when (val result = strideFromWalk(distanceM, steps)) {
                            is StrideResult.Success -> {
                                saveStride(result.strideCm)
                                message = "Stride saved: ${"%.1f".format(result.strideCm)} cm"
                            }
                            is StrideResult.Rejected -> message = "Not saved: ${result.reason}"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .pressScale(buttonSource),
                ) {
                    Text("Stop")
                }
            } else {
                Button(
                    shape = MaterialTheme.shapes.extraLarge,
                    interactionSource = buttonSource,
                    onClick = {
                        message = null
                        gpsPaused = false
                        requestNotifications()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .pressScale(buttonSource),
                ) {
                    Text("Start")
                }
            }
        }

        message?.let {
            Reveal(index = 6, modifier = Modifier.fillMaxWidth()) {
                Text(it, style = MaterialTheme.typography.titleMedium)
            }
        }

        Reveal(index = 7, modifier = Modifier.fillMaxWidth()) {
            Text(
                "GPS works offline. No location or step data is sent anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showGpsDialog) {
        AlertDialog(
            onDismissRequest = {
                showGpsDialog = false
                pendingStart = false
            },
            title = { Text("Turn on location") },
            text = {
                Text(
                    "This walk needs GPS to measure distance. Turn on location, then come back here " +
                        "and the walk will start.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        startSettingsIntent(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                ) {
                    Text("Open location settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showGpsDialog = false
                        pendingStart = false
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

/** Icon chip, label (bodyMedium, onSurfaceVariant) and a headline value, inside a card. */
@Composable
private fun StatCard(
    label: String,
    icon: ImageVector,
    tone: Tone,
    value: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconChip(icon = icon, tone = tone)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                value()
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun isGpsEnabled(locationManager: LocationManager): Boolean =
    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

private fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

private fun startSettingsIntent(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No such settings screen on this device; the dialog or message stays on screen.
    }
}

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
