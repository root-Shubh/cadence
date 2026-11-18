package com.cadence.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cadence.steps.hasActivityRecognition
import com.cadence.steps.notificationPermissionGranted

/** Preferences that remember which runtime permissions the panel has already asked for. */
private const val ASKED_PREFS = "permission_prompts"

/**
 * Card listing the permissions Stepwise uses, with a status and a button for each. Statuses are
 * read again on every ON_RESUME, so a change made in system settings shows when the user returns.
 */
@Composable
fun PermissionsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activity = remember(context) { context.findActivity() }

    // Each read depends on [refresh], so the statuses are read again on every resume.
    val activityGranted = remember(refresh) { hasActivityRecognition(context) }
    val locationGranted = remember(refresh) { hasAnyLocation(context) }
    val notificationsGranted = remember(refresh) { notificationPermissionGranted(context) }
    val gpsOn = remember(refresh) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    }

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        markAsked(context, Manifest.permission.ACTIVITY_RECOGNITION)
        refresh++
    }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        markAsked(context, Manifest.permission.ACCESS_FINE_LOCATION)
        refresh++
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        markAsked(context, Manifest.permission.POST_NOTIFICATIONS)
        refresh++
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Permissions", style = MaterialTheme.typography.titleMedium)

            PermissionRow(
                name = "Physical activity",
                purpose = "Required for steps",
                granted = activityGranted,
                canRequest = canRequestRuntime(
                    context,
                    activity,
                    Manifest.permission.ACTIVITY_RECOGNITION,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                    activityGranted,
                ),
                onAllow = { activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
                onOpenSettings = { startIntent(context, appSettingsIntent(context)) },
                manageable = true,
            )

            PermissionRow(
                name = "Location",
                purpose = "Needed for GPS calibration and vehicle detection",
                granted = locationGranted,
                canRequest = canRequestRuntime(
                    context,
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    true,
                    locationGranted,
                ),
                onAllow = {
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
                onOpenSettings = { startIntent(context, appSettingsIntent(context)) },
                manageable = true,
            )

            PermissionRow(
                name = "Notifications",
                purpose = "Shows the step count in the tray",
                granted = notificationsGranted,
                canRequest = canRequestRuntime(
                    context,
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                    notificationsGranted,
                ),
                onAllow = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onOpenSettings = { startIntent(context, appSettingsIntent(context)) },
                manageable = true,
            )

            PermissionRow(
                name = "Location services",
                purpose = "Turns on GPS for distance measurement",
                granted = gpsOn,
                offLabel = true,
                canRequest = false,
                turnOnLabel = "Turn on",
                onTurnOn = { startIntent(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                onOpenSettings = { startIntent(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            )

            TextButton(
                onClick = { startIntent(context, appSettingsIntent(context)) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Manage permissions in system settings" },
            ) {
                Text("Manage in system settings")
            }
            Text(
                "Android only lets you revoke permissions in system settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One permission row: name, purpose, status and a button of at least 48 dp. [offLabel] shows
 * "Off" instead of "Not granted" (location services). When not granted, the button is "Allow"
 * when a runtime request is possible, otherwise "Open settings"; [turnOnLabel] replaces both.
 */
@Composable
private fun PermissionRow(
    name: String,
    purpose: String,
    granted: Boolean,
    canRequest: Boolean,
    onAllow: () -> Unit = {},
    onOpenSettings: () -> Unit,
    offLabel: Boolean = false,
    turnOnLabel: String? = null,
    onTurnOn: () -> Unit = {},
    manageable: Boolean = false,
) {
    val status = when {
        granted -> "Granted"
        offLabel -> "Off"
        else -> "Not granted"
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$name: $status. $purpose" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                purpose,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                status,
                style = MaterialTheme.typography.labelLarge,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        if (!granted) {
            when {
                turnOnLabel != null -> OutlinedButton(
                    onClick = onTurnOn,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "$turnOnLabel, $name" },
                ) {
                    Text(turnOnLabel)
                }
                canRequest -> Button(
                    onClick = onAllow,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Allow $name" },
                ) {
                    Text("Allow")
                }
                else -> OutlinedButton(
                    onClick = onOpenSettings,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Open settings for $name" },
                ) {
                    Text("Open settings")
                }
            }
        }
    }

    }
}

/** True when fine or coarse location is granted. */
private fun hasAnyLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * True when the runtime dialog can still be shown for [permission]: the platform has the
 * runtime permission ([platformHasRuntime]), it is not granted, and either it was never asked
 * or the system still allows a rationale. Asked before and no rationale left means permanently
 * denied, so the panel offers settings instead.
 */
private fun canRequestRuntime(
    context: Context,
    activity: Activity?,
    permission: String,
    platformHasRuntime: Boolean,
    granted: Boolean,
): Boolean {
    if (granted || !platformHasRuntime || activity == null) return false
    val asked = context.getSharedPreferences(ASKED_PREFS, Context.MODE_PRIVATE)
        .getBoolean(permission, false)
    if (!asked) return true
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
}

private fun markAsked(context: Context, permission: String) {
    context.getSharedPreferences(ASKED_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(permission, true)
        .apply()
}

private fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

private fun startIntent(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No such settings screen on this device; the row stays as it is.
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
