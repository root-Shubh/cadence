package com.cadence.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cadence.MainActivity
import com.cadence.ui.Destination

/** Intent extra naming the [Destination] MainActivity should select on launch. */
const val EXTRA_DESTINATION = "destination"

/**
 * Opens MainActivity on [destination] (or its default tab when null). Each caller passes its own
 * [requestCode] so PendingIntents with different extras do not replace one another.
 */
fun openAppPendingIntent(context: Context, requestCode: Int, destination: Destination?): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    destination?.let { intent.putExtra(EXTRA_DESTINATION, it.name) }
    return PendingIntent.getActivity(context, requestCode, intent, pendingFlags())
}

internal fun pendingFlags(): Int =
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
