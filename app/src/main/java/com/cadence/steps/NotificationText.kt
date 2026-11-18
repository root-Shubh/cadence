package com.cadence.steps

import java.util.Locale

/**
 * Text for the ongoing step notification.
 *
 * [percent] is not capped, so going over the goal reads "120% of 10,000 goal".
 * [remaining] is never negative.
 */
data class NotificationText(
    val title: String,
    val text: String,
    val remainingLine: String,
    val percent: Int,
    val goal: Int,
    val remaining: Int,
)

/**
 * Builds the notification text for [steps] against [goal]. A goal of 0 or less uses
 * [DEFAULT_DAILY_GOAL]. Negative steps count as 0.
 */
fun notificationLines(steps: Int, goal: Int): NotificationText {
    val target = if (goal > 0) goal else DEFAULT_DAILY_GOAL
    val safeSteps = steps.coerceAtLeast(0)
    val percent = (safeSteps * 100L / target).toInt()
    val remaining = (target - safeSteps).coerceAtLeast(0)
    val remainingLine = if (remaining == 0) {
        "Goal reached"
    } else {
        "${formatCount(remaining)} steps to go"
    }
    return NotificationText(
        title = "${formatCount(safeSteps)} steps today",
        text = "$percent% of ${formatCount(target)} goal",
        remainingLine = remainingLine,
        percent = percent,
        goal = target,
        remaining = remaining,
    )
}

/** Thousands-separated number, always with the same separator (Locale.ROOT). */
fun formatCount(value: Int): String = String.format(Locale.ROOT, "%,d", value)
