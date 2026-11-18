package com.stepwise.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.stepwise.steps.NotificationAction
import com.stepwise.steps.decideNotificationAction
import com.stepwise.steps.notificationPermissionGranted

/**
 * Returns a function that runs [onDone] once counting may start. On Android 13+ with the
 * notification permission not granted, it first asks for it; [onDone] runs after the answer,
 * granted or denied. Otherwise [onDone] runs at once.
 */
@Composable
fun rememberNotificationRequest(onDone: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnDone by rememberUpdatedState(onDone)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> currentOnDone() }
    return {
        val granted = notificationPermissionGranted(context)
        when (decideNotificationAction(Build.VERSION.SDK_INT, granted)) {
            NotificationAction.Request -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            NotificationAction.Skip -> currentOnDone()
        }
    }
}
