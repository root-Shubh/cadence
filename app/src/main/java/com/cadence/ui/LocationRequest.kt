package com.cadence.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.cadence.steps.hasFineLocation

/**
 * Returns a function that runs [onDone] once counting may start. When location is not granted
 * it first asks for it; [onDone] runs after the answer, granted or denied, so counting still
 * works without location (the vehicle check is then off).
 */
@Composable
fun rememberLocationRequest(onDone: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnDone by rememberUpdatedState(onDone)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ -> currentOnDone() }
    return {
        if (hasFineLocation(context)) {
            currentOnDone()
        } else {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }
}
