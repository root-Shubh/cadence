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
        val trigger = action.substringAfterLast('.')
        // resumeCountingIfNeeded logs its decision; only a refused start is logged here.
        runCatching { resumeCountingIfNeeded(appContext, trigger) }.onFailure { e ->
            DebugLog.add("service", "$trigger: restart refused: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
