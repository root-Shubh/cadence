package com.cadence.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.cadence.R
import com.cadence.ui.Destination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Resizable goals widget: three rings (Daily, Weekly, Monthly), each with its percent and steps
 * against goal. Tapping a ring opens the Profile goals card; tapping anywhere else opens Today.
 */
class GoalsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                refreshGoalsWidgets(appContext, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_ROOT = 20
        private const val REQUEST_RING_DAILY = 21
        private const val REQUEST_RING_WEEKLY = 22
        private const val REQUEST_RING_MONTHLY = 23
        private const val RING_PX = 144
        private const val RING_STROKE_PX = 16f

        internal fun buildViews(context: Context, snapshot: GoalsSnapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_goals)
            views.setOnClickPendingIntent(
                R.id.goals_root,
                openAppPendingIntent(context, REQUEST_ROOT, Destination.TODAY),
            )
            val progressColor = ContextCompat.getColor(context, R.color.widget_ring_progress)
            val trackColor = ContextCompat.getColor(context, R.color.widget_ring_track)
            bindPeriod(
                context, views, snapshot.daily, progressColor, trackColor,
                ids = PeriodViews(R.id.goal_ring_daily, R.id.goal_percent_daily, R.id.goal_text_daily),
                requestCode = REQUEST_RING_DAILY,
            )
            bindPeriod(
                context, views, snapshot.weekly, progressColor, trackColor,
                ids = PeriodViews(R.id.goal_ring_weekly, R.id.goal_percent_weekly, R.id.goal_text_weekly),
                requestCode = REQUEST_RING_WEEKLY,
            )
            bindPeriod(
                context, views, snapshot.monthly, progressColor, trackColor,
                ids = PeriodViews(R.id.goal_ring_monthly, R.id.goal_percent_monthly, R.id.goal_text_monthly),
                requestCode = REQUEST_RING_MONTHLY,
            )
            return views
        }

        private data class PeriodViews(val ring: Int, val percent: Int, val text: Int)

        private fun bindPeriod(
            context: Context,
            views: RemoteViews,
            period: PeriodProgress,
            progressColor: Int,
            trackColor: Int,
            ids: PeriodViews,
            requestCode: Int,
        ) {
            val label = goalRingLabel(period.steps, period.goal ?: 0)
            val ringBitmap = renderRing(
                sizePx = RING_PX,
                fraction = label.fraction,
                progressColor = progressColor,
                trackColor = trackColor,
                strokePx = RING_STROKE_PX,
            )
            views.setImageViewBitmap(ids.ring, ringBitmap)
            if (label.hasGoal) {
                views.setTextViewText(
                    ids.percent,
                    context.getString(R.string.widget_goal_percent, label.percent),
                )
                views.setTextViewText(ids.text, label.text)
            } else {
                views.setTextViewText(ids.percent, context.getString(R.string.widget_goal_unset_percent))
                views.setTextViewText(ids.text, context.getString(R.string.widget_goal_unset))
            }
            views.setOnClickPendingIntent(
                ids.ring,
                openAppPendingIntent(context, requestCode, Destination.PROFILE),
            )
        }
    }
}
