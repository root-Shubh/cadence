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

/** "64%" of the daily goal for the steps widget, or empty when no goal is set. Not capped at 100. */
fun widgetPercentText(steps: Int, goal: Int?): String {
    if (goal == null || goal <= 0) return ""
    return "${maxOf(0, steps).toLong() * 100 / goal}%"
}

/** Progress value for a progress bar whose maximum is [goal], clamped to 0..goal. */
fun widgetProgress(steps: Int, goal: Int): Int {
    if (goal <= 0) return 0
    return steps.coerceIn(0, goal)
}

/** Result of [goalRingLabel]. [percent] is not capped, so a goal passed shows above 100. */
data class RingLabel(
    val percent: Int,
    val fraction: Float,
    val hasGoal: Boolean,
    val text: String,
)

/**
 * Label for one goal ring. The ring fills to at most 100 percent. Without a goal the percent is 0
 * and [RingLabel.hasGoal] is false, so the caller shows its "no goal" text instead.
 */
fun goalRingLabel(steps: Int, goal: Int): RingLabel {
    val done = maxOf(0, steps)
    if (goal <= 0) return RingLabel(percent = 0, fraction = 0f, hasGoal = false, text = "")
    val percent = (done.toLong() * 100 / goal).toInt()
    val fraction = (done.toFloat() / goal).coerceIn(0f, 1f)
    return RingLabel(
        percent = percent,
        fraction = fraction,
        hasGoal = true,
        text = "${compactCount(done)} / ${compactCount(goal)}",
    )
}

/** Short step count for narrow widget cells: 950, 6.5k, 12k, 1.2M. Negative values show as 0. */
fun compactCount(value: Int): String {
    val v = maxOf(0, value)
    return when {
        v < 1_000 -> v.toString()
        v < 1_000_000 -> trimmedOneDecimal(v / 1_000.0) + "k"
        else -> trimmedOneDecimal(v / 1_000_000.0) + "M"
    }
}

private fun trimmedOneDecimal(x: Double): String {
    val s = String.format(Locale.ROOT, "%.1f", x)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

/** Button text for the start/stop widget. The defaults are English; the widget passes localized text. */
fun actionLabel(counting: Boolean, startText: String = "Start", stopText: String = "Stop"): String =
    if (counting) stopText else startText
