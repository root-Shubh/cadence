package com.cadence.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.cadence.MainActivity
import com.cadence.R
import com.cadence.data.DEFAULT_PROFILE_ID
import com.cadence.data.GoalsStore
import com.cadence.data.ProfileStore
import com.cadence.data.StepDatabase
import com.cadence.steps.currentTodaySteps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Home-screen widget showing today's steps and progress toward the daily goal.
 * Data is read on a background thread; the PendingResult is finished once views are updated.
 */
class StepsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snapshot = loadSnapshot(appContext)
                for (id in appWidgetIds) {
                    appWidgetManager.updateAppWidget(id, buildViews(appContext, snapshot))
                }
            } catch (e: Exception) {
                // Storage failed: show zero progress rather than leaving stale text.
                val fallback = Snapshot(steps = 0, goal = null)
                for (id in appWidgetIds) {
                    appWidgetManager.updateAppWidget(id, buildViews(appContext, fallback))
                }
            } finally {
                // The step service redraws all three widgets itself (requestAllWidgetsRefresh).
                pending.finish()
            }
        }
    }

    private data class Snapshot(val steps: Int, val goal: Int?)

    private suspend fun loadSnapshot(context: Context): Snapshot {
        val profileId = ProfileStore(context).activeProfileId() ?: DEFAULT_PROFILE_ID
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        val steps = currentTodaySteps(context, profileId, today)
        val goal = GoalsStore(context).load().dailySteps
        return Snapshot(steps = steps, goal = goal)
    }

    private fun buildViews(context: Context, snapshot: Snapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_steps)
        val max = snapshot.goal?.takeIf { it > 0 } ?: WIDGET_DEFAULT_MAX
        views.setTextViewText(R.id.widget_steps, widgetStepsText(snapshot.steps))
        views.setTextViewText(R.id.widget_goal, widgetGoalText(snapshot.steps, snapshot.goal))
        views.setTextViewText(R.id.widget_percent, widgetPercentText(snapshot.steps, snapshot.goal))
        views.setProgressBar(R.id.widget_progress, max, widgetProgress(snapshot.steps, max), false)
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingOpen = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingOpen)
        return views
    }
}
