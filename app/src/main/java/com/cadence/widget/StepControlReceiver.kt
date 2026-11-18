package com.cadence.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cadence.steps.isCountingOn
import com.cadence.steps.setCountingOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles a tap on the start/stop widget. Starts or stops counting through setCountingOn, then
 * redraws the widgets. Not exported, so only PendingIntents created by this app reach it.
 */
class StepControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (canToggleCounting(appContext)) {
                    setCountingOn(appContext, !isCountingOn(appContext))
                } else {
                    // Permission or step source is missing: open the app so it can show the prompt.
                    openAppPendingIntent(appContext, REQUEST_OPEN_APP, null).send()
                }
                refreshAllWidgets(appContext)
            } catch (e: Exception) {
                // Leave the widgets as they are; the next update redraws them.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.cadence.widget.action.TOGGLE_COUNTING"
        private const val REQUEST_OPEN_APP = 12
    }
}
