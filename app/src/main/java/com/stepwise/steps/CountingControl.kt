package com.stepwise.steps

import android.content.Context

/** Whether the user wants counting on. Persisted, so counting resumes after the app restarts. */
fun isCountingOn(context: Context): Boolean =
    context.getSharedPreferences(StepCounterService.PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(StepCounterService.KEY_COUNTING_ON, false)

/** Turns counting on (saves the flag and starts the service) or off (saves the flag and stops it). */
fun setCountingOn(context: Context, on: Boolean) {
    val app = context.applicationContext
    app.getSharedPreferences(StepCounterService.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(StepCounterService.KEY_COUNTING_ON, on)
        .apply()
    if (on) StepCounterService.start(app) else StepCounterService.stop(app)
}

/**
 * On app launch: restarts the service when counting is on and the permission and a step
 * source are present. Returns true when the service was started.
 */
fun resumeCountingIfNeeded(context: Context): Boolean {
    if (!isCountingOn(context)) return false
    if (!hasActivityRecognition(context)) return false
    if (stepTrackingSource(context) is StepTrackingSource.None) return false
    StepCounterService.start(context.applicationContext)
    return true
}
