package com.cadence.ui

import android.os.Looper
import android.util.Log
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.material3.Surface
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.cadence.steps.LiveSteps
import com.cadence.steps.StepTrackingSource
import com.cadence.steps.StrideStore
import com.cadence.steps.hasActivityRecognition
import com.cadence.steps.isCountingOn
import com.cadence.steps.setCountingOn
import com.cadence.steps.stepTrackingSource

/** Steps at which a calibration walk stops on its own. */
const val CALIBRATION_TARGET_STEPS: Int = 50

/** Metres a calibration walk must cover before it can stop on its own. */
const val CALIBRATION_TARGET_METERS: Double = 50.0

/**
 * True once a calibration walk has both [targetSteps] steps and [targetMeters] metres, so it
 * stops on its own. The user can still press Stop to end it earlier.
 */
fun shouldAutoStop(
    steps: Int,
    distanceM: Double,
    targetSteps: Int = CALIBRATION_TARGET_STEPS,
    targetMeters: Double = CALIBRATION_TARGET_METERS,
): Boolean = steps >= targetSteps && distanceM >= targetMeters

/**
 * [saveStride] stores a successful stride and returns false when nothing could be saved.
 * [savedStrideCm] is the stride currently saved in the profile, shown on the screen.
 */
@Composable
fun CalibrateScreen(
    saveStride: (Double) -> Boolean,
    savedStrideCm: Double?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locationManager = remember {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val walk = remember { WalkHolder() }
    val buttonSource = remember { MutableInteractionSource() }
    // Set when this screen turned counting on, so it can turn it off again.
    val counting = remember { CountingHolder() }

    // Live total from the step service. The walk shows the steps since it started.
    val liveTotal by LiveSteps.steps.collectAsState()
    val tracking by LiveSteps.tracking.collectAsState()

    var walking by remember { mutableStateOf(false) }
    // Total at the moment the walk's counting began; null until the step service is tracking.
    var baseline by remember { mutableStateOf<Int?>(null) }
    // Steps of the last walk, or of the walk so far while paused.
    var lastSteps by remember { mutableIntStateOf(0) }
    // The GPS-learned stride. Read again whenever a walk starts or stops, when the service may have learned more.
    val learnedStride = remember(walking) { StrideStore(context).load() }
    var permissionDenied by remember { mutableStateOf(false) }
    var distanceM by remember { mutableDoubleStateOf(0.0) }
    // Latest GPS fix accuracy in metres (any fix, accepted or not), and how many fixes were used.
    var gpsAccuracyM by remember { mutableStateOf<Float?>(null) }
    var gpsFixesUsed by remember { mutableIntStateOf(0) }
    var gnssInView by remember { mutableIntStateOf(0) }
    var gnssUsed by remember { mutableIntStateOf(0) }
    var lastFixMillis by remember { mutableStateOf<Long?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    // True when the user asked to start and the walk waits for permission and GPS.
    var pendingStart by remember { mutableStateOf(false) }
    var showGpsDialog by remember { mutableStateOf(false) }
    // Bumped on every ON_RESUME (for example, back from location settings) to re-check GPS.
    var resumeCount by remember { mutableIntStateOf(0) }
    // True when GPS was switched off mid-walk: the walk is paused and resumes once GPS is back on.
    var gpsPaused by remember { mutableStateOf(false) }
    // Bumped on every location provider change while a walk runs or is paused, and on resume.
    var gpsEvents by remember { mutableIntStateOf(0) }

    // Steps since the walk started. Zero before the step service is tracking.
    val shownSteps = if (walking) {
        baseline?.let { (liveTotal - it).coerceAtLeast(0) } ?: 0
    } else {
        lastSteps
    }

    val listener = remember {
        object : LocationListener {
            override fun onLocationChanged(location: Location) {
                gpsAccuracyM = location.accuracy
                lastFixMillis = System.currentTimeMillis()
                val before = walk.distance.acceptedFixCount
                Log.d(
                    GPS_LOG_TAG,
                    "fix lat=${"%.5f".format(location.latitude)} lon=${"%.5f".format(location.longitude)} " +
                        "acc=${"%.1f".format(location.accuracy)}m speed=${"%.2f".format(location.speed)}m/s " +
                        "time=${location.time} provider=${location.provider}",
                )
                distanceM = walk.distance.addFix(
                    GpsFix(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyM = location.accuracy,
                        timeMillis = location.time,
                    ),
                )
                gpsFixesUsed = walk.distance.acceptedFixCount
                if (gpsFixesUsed == before) {
                    Log.d(GPS_LOG_TAG, "fix rejected (accuracy above ${GpsDistance.MAX_ACCURACY_M} m, out of order, or too fast)")
                }
                Log.d(GPS_LOG_TAG, "distance=${"%.1f".format(distanceM)}m fixes=$gpsFixesUsed")
            }

            override fun onProviderEnabled(provider: String) {
                Log.d(GPS_LOG_TAG, "provider enabled: $provider")
            }

            override fun onProviderDisabled(provider: String) {
                Log.d(GPS_LOG_TAG, "provider disabled: $provider")
            }

            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        }
    }

    // Takes the baseline once the step service is tracking, so steps before the walk are not counted.
    LaunchedEffect(walking, tracking) {
        if (walking && tracking && baseline == null) {
            baseline = LiveSteps.steps.value
        }
    }

    // Ends the walk: the result is shown, and saved when it is valid.
    fun finishWalk() {
        val steps = shownSteps
        walking = false
        gpsPaused = false
        baseline = null
        lastSteps = steps
        stopCountingIfStarted(context, counting)
        when (val result = strideFromWalk(distanceM, steps)) {
            is StrideResult.Success -> {
                message = if (saveStride(result.strideCm)) {
                    "Stride saved: ${"%.1f".format(result.strideCm)} cm, used for distance from now on"
                } else {
                    "Not saved: create a profile first"
                }
            }
            is StrideResult.Rejected -> message = "Not saved: ${result.reason}"
        }
    }

    // Stops the walk on its own at the target step count. The user can still press Stop earlier.
    LaunchedEffect(walking, shownSteps, distanceM) {
        if (walking && shouldAutoStop(shownSteps, distanceM)) finishWalk()
    }

    // GPS listener is registered only while walking and removed on Stop or when leaving the screen.
    // GPS stays warm while this screen is open, so a lock is quick when a walk starts. Satellite
    // status comes from the GNSS callback and is shown on screen.
    DisposableEffect(Unit) {
        val handler = android.os.Handler(Looper.getMainLooper())
        val gnss = object : android.location.GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: android.location.GnssStatus) {
                var used = 0
                for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
                gnssInView = status.satelliteCount
                gnssUsed = used
            }
        }
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasFine) {
            try {
                startGps(locationManager, listener)
                locationManager.registerGnssStatusCallback(gnss, handler)
            } catch (e: SecurityException) {
                Log.w(GPS_LOG_TAG, "GPS not started: permission", e)
            }
        }
        onDispose {
            locationManager.removeUpdates(listener)
            try { locationManager.unregisterGnssStatusCallback(gnss) } catch (e: SecurityException) { }
        }
    }

    // Leaving the screen ends any counting this screen started.
    DisposableEffect(Unit) {
        onDispose { stopCountingIfStarted(context, counting) }
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
        if (!isGpsEnabled(locationManager)) {
            pendingStart = true
            showGpsDialog = true
            return
        }
        pendingStart = false
        showGpsDialog = false
        if (!hasActivityRecognition(context)) {
            message = "Allow physical activity in Permissions to count steps."
            return
        }
        if (stepTrackingSource(context) is StepTrackingSource.None) {
            message = "This phone has no step sensor."
            return
        }
        // Calibration counts steps itself: turn counting on when it is not already running.
        if (!LiveSteps.tracking.value) {
            counting.startedHere = !isCountingOn(context)
            setCountingOn(context, true)
        }
        walk.distance = GpsDistance()
        distanceM = 0.0
        baseline = null
        lastSteps = 0
        message = null
        walking = true
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

    // GPS going off only pauses the distance. Steps keep counting: they never depend on GPS.
    LaunchedEffect(gpsEvents) {
        if (gpsEvents == 0) return@LaunchedEffect
        val enabled = isGpsEnabled(locationManager)
        if (walking && !enabled) {
            gpsPaused = true
        } else if (gpsPaused && enabled) {
            gpsPaused = false
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Reveal(index = 0, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Calibrate stride", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Walk a measured route of at least 50 m and 50 steps. The walk stops by itself " +
                        "at $CALIBRATION_TARGET_STEPS steps, or press Stop earlier. " +
                        "Your stride length is calculated from the distance and steps.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
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
                            "GPS is off. Steps still count, but distance is paused until location is back on.",
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
            val nowMs by produceState(System.currentTimeMillis()) {
                while (true) {
                    value = System.currentTimeMillis()
                    kotlinx.coroutines.delay(1000L)
                }
            }
            val gpsOnNow = isGpsEnabled(locationManager)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("GPS", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            !gpsOnNow -> "GPS is off. Turn on location services."
                            lastFixMillis == null -> "Searching for satellites..."
                            else -> "Locked"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Satellites in view: $gnssInView, used in fix: $gnssUsed", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Accuracy: " + (gpsAccuracyM?.let { "%.0f m".format(it) } ?: "none yet"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Last fix: " + (lastFixMillis?.let { ((nowMs - it) / 1000).toString() + " s ago" } ?: "none yet") +
                            " · Fixes used for distance: $gpsFixesUsed",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            if (walking) {
                Text(
                    text = when {
                        gpsFixesUsed == 0 && gpsAccuracyM == null ->
                            "Waiting for GPS. Walk outdoors with a clear view of the sky."
                        gpsFixesUsed == 0 ->
                            "GPS accuracy is ${"%.0f".format(gpsAccuracyM)} m. Needs 15 m or better to count distance."
                        else -> "GPS locked ($gpsFixesUsed fixes), accuracy ${"%.0f".format(gpsAccuracyM ?: 0f)} m"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }
        }
        Reveal(index = 3, modifier = Modifier.fillMaxWidth()) {
            StatCard(
                label = "Steps during walk",
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                tone = Tone.PRIMARY,
                value = {
                    AnimatedCountText(
                        value = shownSteps,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
            )
        }

        if (!walking && message == null) {
            Reveal(index = 4, modifier = Modifier.fillMaxWidth()) {
                val learnedCm = learnedStride.usableCm()
                Text(
                    when {
                        savedStrideCm != null ->
                            "Saved stride: ${"%.1f".format(savedStrideCm)} cm. It is used for distance " +
                                "until you calibrate again."
                        learnedCm != null ->
                            "Stride learned from walking: ${"%.0f".format(learnedCm)} cm " +
                                "(based on ${learnedStride.walks} ${if (learnedStride.walks == 1) "walk" else "walks"})"
                        else -> "Your stride is estimated from your height until you calibrate."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Start and Stop are outside the entrance animation, so the button is full size and
        // visible from the first frame.
        // A plain surface with explicit colours, so the label cannot be hidden by button defaults.
        val actionLabel = if (walking) "Stop" else "Start"
        val actionColor = if (walking) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary
        val actionText = if (walking) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = actionColor,
            contentColor = actionText,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .pressScale(buttonSource)
                .clickable(
                    interactionSource = buttonSource,
                    indication = null,
                    role = Role.Button,
                ) {
                    if (walking) {
                        finishWalk()
                    } else {
                        message = null
                        gpsPaused = false
                        requestNotifications()
                    }
                },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.titleMedium, color = actionText)
            }
        }

        message?.let {
            Reveal(index = 6, modifier = Modifier.fillMaxWidth()) {
                Text(it, style = MaterialTheme.typography.titleMedium)
            }
        }

        Reveal(index = 5, modifier = Modifier.fillMaxWidth()) {
            PermissionsPanel()
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

/** Turns counting off again when this screen turned it on. */
private fun stopCountingIfStarted(context: Context, counting: CountingHolder) {
    if (counting.startedHere) {
        counting.startedHere = false
        setCountingOn(context, false)
    }
}

/** Holds the distance tracker for the current walk; a new one is created per walk. */
private class WalkHolder {
    var distance = GpsDistance()
}

/** True when this screen turned step counting on and must turn it off again. */
private class CountingHolder {
    var startedHere = false
}

/** Only called after location permission has been granted. */
@SuppressLint("MissingPermission")
private fun startGps(locationManager: LocationManager, listener: LocationListener) {
    // No minimum time or distance: every fix the GPS produces is delivered on the main looper.
    Log.d(GPS_LOG_TAG, "requesting GPS updates")
    locationManager.requestLocationUpdates(
        LocationManager.GPS_PROVIDER,
        0L,
        0f,
        listener,
        Looper.getMainLooper(),
    )
}

private const val GPS_LOG_TAG = "CadenceGps"
