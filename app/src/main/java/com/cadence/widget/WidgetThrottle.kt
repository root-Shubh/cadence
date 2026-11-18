package com.cadence.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Limits widget redraws to one per [intervalMs] without ever losing the last one. A request inside
 * the interval is remembered, and [takePendingIfDue] sends it once the interval has passed, so the
 * final count of a walk always reaches the home screen. Pure Kotlin, not thread-safe: the step
 * service uses it from its single consumer.
 */
class WidgetThrottle(private val intervalMs: Long = WIDGET_REDRAW_INTERVAL_MS) {
    private var lastSentMillis: Long? = null
    private var pending = false

    /** A redraw is wanted. Returns true when it should be sent now; otherwise it is kept for later. */
    fun request(nowMillis: Long): Boolean {
        val last = lastSentMillis
        if (last == null || nowMillis - last !in 0 until intervalMs) {
            lastSentMillis = nowMillis
            pending = false
            return true
        }
        pending = true
        return false
    }

    /** True when a kept redraw is now due; it is then marked as sent. */
    fun takePendingIfDue(nowMillis: Long): Boolean {
        if (!pending) return false
        return request(nowMillis)
    }

    /** True while a redraw is waiting for the interval to pass. */
    fun hasPending(): Boolean = pending
}

/** Shortest time between two step-driven widget redraws. */
const val WIDGET_REDRAW_INTERVAL_MS: Long = 5_000L

/**
 * Asks every placed Cadence widget to redraw now. Sends ACTION_APPWIDGET_UPDATE with the widget
 * ids: AppWidgetProvider only calls onUpdate when the ids are in the intent, so a broadcast
 * without them redraws nothing.
 */
fun requestAllWidgetsRefresh(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val providers = listOf(
        StepsWidgetProvider::class.java,
        StartStopWidgetProvider::class.java,
        GoalsWidgetProvider::class.java,
    )
    for (provider in providers) {
        val component = ComponentName(context, provider)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) continue
        context.sendBroadcast(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setComponent(component)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
        )
    }
}
