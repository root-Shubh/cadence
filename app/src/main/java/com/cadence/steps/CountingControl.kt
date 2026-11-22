package com.cadence.steps

import android.content.Context
import com.cadence.debug.DebugLog

/** Whether the user wants counting on. Persisted, so counting resumes after the app restarts. */
fun isCountingOn(context: Context): Boolean =
    context.getSharedPreferences(StepCounterService.PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(StepCounterService.KEY_COUNTING_ON, false)

/**
 * True while the step service is really counting. Use this, not the saved flag, to decide what a
 * Start or Stop press does: the flag can outlive the service.
 */
fun isCountingRunning(): Boolean = LiveSteps.tracking.value

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
 * On app launch or boot: restarts the service when counting is on and the permission and a step
 * source are present. Returns true when the service was started. [trigger] names the caller in
 * the debug log, which records the decision either way.
 */
fun resumeCountingIfNeeded(context: Context, trigger: String = "app launch"): Boolean {
    val decision = resumeDecision(
        countingOn = isCountingOn(context),
        hasPermission = hasActivityRecognition(context),
        hasSource = stepTrackingSource(context) !is StepTrackingSource.None,
    )
    DebugLog.add("service", "$trigger: ${decision.reason}")
    if (decision.resumes) StepCounterService.start(context.applicationContext)
    return decision.resumes
}
