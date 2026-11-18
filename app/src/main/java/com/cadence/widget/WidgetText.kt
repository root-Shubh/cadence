package com.cadence.widget

import java.util.Locale

/** Default progress-bar maximum when no daily goal is set. */
const val WIDGET_DEFAULT_MAX = 10_000

private const val NO_GOAL_TEXT = "Set a goal in the app"

/** Step count with thousands separators. Negative counts are shown as 0. */
fun widgetStepsText(steps: Int): String = String.format(Locale.ROOT, "%,d", maxOf(0, steps))

/** "of N goal" when a goal is set, otherwise a hint to set one. [steps] does not change the text. */
fun widgetGoalText(steps: Int, goal: Int?): String {
    if (goal == null || goal <= 0) return NO_GOAL_TEXT
    return "of ${String.format(Locale.ROOT, "%,d", goal)} goal"
}

/** Progress value for a progress bar whose maximum is [goal], clamped to 0..goal. */
fun widgetProgress(steps: Int, goal: Int): Int {
    if (goal <= 0) return 0
    return steps.coerceIn(0, goal)
}
