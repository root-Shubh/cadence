package com.cadence.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.cadence.R
import com.cadence.ui.Destination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Small "Start / Stop" widget. The whole widget is the button. A tap goes to [StepControlReceiver]
 * when counting can start; otherwise it opens the app so the permission prompt can be shown there.
 */
class StartStopWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                refreshStartStopWidgets(appContext, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_TOGGLE = 10
        private const val REQUEST_OPEN_APP = 11

        internal fun componentName(context: Context): ComponentName =
            ComponentName(context, StartStopWidgetProvider::class.java)

        internal fun buildViews(
            context: Context,
            steps: Int,
            counting: Boolean,
            canToggle: Boolean,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_start_stop)
            views.setTextViewText(
                R.id.widget_action,
                actionLabel(
                    counting,
                    startText = context.getString(R.string.widget_action_start),
                    stopText = context.getString(R.string.widget_action_stop),
                ),
            )
            views.setTextViewText(
                R.id.widget_steps,
                context.getString(R.string.widget_steps_short, widgetStepsText(steps)),
            )
            views.setOnClickPendingIntent(R.id.widget_root, clickIntent(context, canToggle))
            return views
        }

        private fun clickIntent(context: Context, canToggle: Boolean): PendingIntent =
            if (canToggle) {
                val toggle = Intent(context, StepControlReceiver::class.java)
                    .setAction(StepControlReceiver.ACTION_TOGGLE)
                PendingIntent.getBroadcast(context, REQUEST_TOGGLE, toggle, pendingFlags())
            } else {
                openAppPendingIntent(context, REQUEST_OPEN_APP, Destination.TODAY)
            }
    }
}
