package com.stepwise.data

import android.content.Context
import android.util.Log
import com.stepwise.steps.LiveSteps
import com.stepwise.steps.setCountingOn
import java.io.File

private const val TAG = "StepwiseReset"

/** SharedPreferences files that hold app state. A fresh install has none of them. */
fun freshInstallPrefFiles(): List<String> = listOf(
    "stepwise_profile",
    "stepwise_goals",
    "stepwise_theme",
    "stepwise_privacy",
    "stepwise_onboarding",
    "stepwise_tracker",
)

/**
 * Clears every app preference file with commit() and checks each result. A failure is logged
 * and the remaining files are still cleared. Returns true only when all of them committed.
 */
fun clearFreshInstallPrefs(context: Context): Boolean {
    var allCleared = true
    for (name in freshInstallPrefFiles()) {
        try {
            val committed = context.applicationContext
                .getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
            if (!committed) {
                allCleared = false
                Log.w(TAG, "Could not commit clear of $name")
            }
        } catch (e: RuntimeException) {
            allCleared = false
            Log.w(TAG, "Could not clear $name", e)
        }
    }
    return allCleared
}

/**
 * Resets the app to a fresh install's state: stops counting, deletes every profile's step
 * history, clears the app preference files and removes exported CSV cache files. The caller
 * then recreates the activity so the onboarding gate runs again.
 */
suspend fun resetToFreshInstall(context: Context, dataReset: DataReset) {
    val app = context.applicationContext
    setCountingOn(app, false)
    LiveSteps.reset(0)
    dataReset.deleteAllRecordedDataForEveryone()
    clearFreshInstallPrefs(app)
    try {
        File(app.cacheDir, "exports").deleteRecursively()
    } catch (e: SecurityException) {
        Log.w(TAG, "Could not delete export cache", e)
    }
}
