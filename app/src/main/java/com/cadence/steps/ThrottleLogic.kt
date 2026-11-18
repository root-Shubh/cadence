package com.cadence.steps

/** Shortest time between two storage writes while counting, in milliseconds. */
const val STORE_MIN_INTERVAL_MS: Long = 2000L

/** Default daily goal shown in the notification when the user has not set one. */
const val DEFAULT_DAILY_GOAL: Int = 10_000

/**
 * Whether a storage write may happen now. A clock that went backwards also allows a write, so
 * a time change can never block saving.
 */
fun shouldWriteNow(
    lastWriteMillis: Long,
    nowMillis: Long,
    minIntervalMillis: Long = STORE_MIN_INTERVAL_MS,
): Boolean = nowMillis < lastWriteMillis || nowMillis - lastWriteMillis >= minIntervalMillis

/** Progress bar value for the notification: [steps] clamped to 0..[goal]. A goal of 0 or less gives 0. */
fun progressFor(steps: Int, goal: Int): Int =
    if (goal <= 0) 0 else steps.coerceIn(0, goal)
