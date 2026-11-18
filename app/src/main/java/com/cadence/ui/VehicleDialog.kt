package com.cadence.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cadence.steps.VEHICLE_ALERT_TEXT
import com.cadence.steps.VEHICLE_ALERT_TITLE

/** Shown when a vehicle is detected while the app is open. Recording has already stopped. */
@Composable
fun VehicleCheatingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(VEHICLE_ALERT_TITLE) },
        text = { Text(VEHICLE_ALERT_TEXT) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Got it")
            }
        },
    )
}
