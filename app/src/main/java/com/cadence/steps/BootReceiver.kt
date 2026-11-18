package com.cadence.steps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cadence.debug.DebugLog

/**
 * Restarts counting after the phone boots, or after the app is updated, when the user had
 * counting on. Without it counting stays off until the app is opened again. A refused start
 * (Android limits starting foreground services from the background) is logged, not thrown.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val appContext = context.applicationContext
        DebugLog.attach(appContext.filesDir)
        val result = runCatching { resumeCountingIfNeeded(appContext) }
        val outcome = result.fold(
            onSuccess = { resumed -> if (resumed) "counting restarted" else "counting was off; nothing to do" },
            onFailure = { e -> "restart refused: ${e.javaClass.simpleName}: ${e.message}" },
        )
        DebugLog.add("service", "${action.substringAfterLast('.')}: $outcome")
    }
}
