package com.stepwise.steps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** What to do before counting starts: ask for the notification permission, or go straight on. */
enum class NotificationAction { Request, Skip }

/**
 * Pure decision: the POST_NOTIFICATIONS runtime permission exists from Android 13 (API 33).
 * Below that, or when already granted, there is nothing to ask. Otherwise ask, and counting
 * starts after the answer either way (the service must still call startForeground).
 */
fun decideNotificationAction(sdkInt: Int, granted: Boolean): NotificationAction =
    if (sdkInt < Build.VERSION_CODES.TIRAMISU || granted) NotificationAction.Skip
    else NotificationAction.Request

/** True when POST_NOTIFICATIONS is granted, or when no runtime permission exists (below API 33). */
fun notificationPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/** True when the app may show notifications at all (permission and the channel/app switch). */
fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

/** The app's notification settings screen. */
fun appNotificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
