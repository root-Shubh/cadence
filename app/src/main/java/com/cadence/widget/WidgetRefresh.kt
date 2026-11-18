package com.cadence.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.cadence.data.DEFAULT_PROFILE_ID
import com.cadence.data.GoalsStore
import com.cadence.data.ProfileStore
import com.cadence.data.StepDatabase
import com.cadence.goals.effectiveGoals
import com.cadence.steps.StepTrackingSource
import com.cadence.steps.hasActivityRecognition
import com.cadence.steps.currentTodaySteps
import com.cadence.steps.isCountingRunning
import com.cadence.steps.stepTrackingSource
import java.time.LocalDate
import java.time.ZoneId

/** One period's steps and goal. [goal] is null when no goal is set for that period. */
data class PeriodProgress(val steps: Int, val goal: Int?)

/** Today, the rolling 7 days ending today, and the calendar month so far, with goals filled in. */
data class GoalsSnapshot(
    val daily: PeriodProgress,
    val weekly: PeriodProgress,
    val monthly: PeriodProgress,
)

/** True when counting can start: the permission is granted and a step source is present. */
fun canToggleCounting(context: Context): Boolean =
    hasActivityRecognition(context) && stepTrackingSource(context) !is StepTrackingSource.None

/** Reads the stored step rows once and sums them per period. Throws on storage failure. */
suspend fun loadGoalsSnapshot(context: Context): GoalsSnapshot {
    val profileId = ProfileStore(context).activeProfileId() ?: DEFAULT_PROFILE_ID
    val today = LocalDate.now(ZoneId.systemDefault())
    val weekStart = today.minusDays(6)
    val monthStart = today.withDayOfMonth(1)
    val from = minOf(weekStart, monthStart)
    val rows = StepDatabase.getInstance(context).stepDayDao()
        .getRange(profileId, from.toString(), today.toString())
        .mapNotNull { row -> runCatching { LocalDate.parse(row.dateIso) to row.steps }.getOrNull() }

    // Today comes from the live source, so the widgets match Today and the notification.
    val todaySteps = currentTodaySteps(context, profileId, today.toString())
    fun sumSince(start: LocalDate): Int {
        val past = rows.filter { (date, _) -> !date.isBefore(start) && date.isBefore(today) }.sumOf { it.second }
        return past + todaySteps
    }

    val goals = effectiveGoals(GoalsStore(context).load())
    return GoalsSnapshot(
        daily = PeriodProgress(todaySteps, goals.dailySteps),
        weekly = PeriodProgress(sumSince(weekStart), goals.weeklySteps),
        monthly = PeriodProgress(sumSince(monthStart), goals.monthlySteps),
    )
}

/** Redraws every placed start/stop and goals widget now. */
suspend fun refreshAllWidgets(context: Context) {
    refreshStartStopWidgets(context)
    refreshGoalsWidgets(context)
}

/** Redraws the start/stop widgets. [ids] limits it to specific widgets. */
suspend fun refreshStartStopWidgets(context: Context, ids: IntArray? = null) {
    val manager = AppWidgetManager.getInstance(context)
    val target = ids ?: manager.getAppWidgetIds(StartStopWidgetProvider.componentName(context))
    if (target.isEmpty()) return
    val steps = runCatching { loadGoalsSnapshot(context).daily.steps }.getOrDefault(0)
    val views = StartStopWidgetProvider.buildViews(
        context = context,
        steps = steps,
        counting = isCountingRunning(),
        canToggle = canToggleCounting(context),
    )
    for (id in target) manager.updateAppWidget(id, views)
}

/** Redraws the goals widgets. [ids] limits it to specific widgets. Storage failures show zero steps. */
suspend fun refreshGoalsWidgets(context: Context, ids: IntArray? = null) {
    val manager = AppWidgetManager.getInstance(context)
    val target = ids ?: manager.getAppWidgetIds(
        ComponentName(context, GoalsWidgetProvider::class.java),
    )
    if (target.isEmpty()) return
    val snapshot = runCatching { loadGoalsSnapshot(context) }.getOrElse {
        GoalsSnapshot(PeriodProgress(0, null), PeriodProgress(0, null), PeriodProgress(0, null))
    }
    val views = GoalsWidgetProvider.buildViews(context, snapshot)
    for (id in target) manager.updateAppWidget(id, views)
}
