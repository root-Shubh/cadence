package com.cadence.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Onboarding flags in SharedPreferences "stepwise_onboarding": whether the first-launch flow
 * is finished, and when the onboarding profile was saved (epoch millis, for the review banner).
 * Every access is guarded: a failed read returns the default, a failed write is dropped.
 */
class OnboardingPrefs(context: Context) {

    private val prefs: SharedPreferences? = try {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (e: RuntimeException) {
        null
    }

    fun isDone(): Boolean = try {
        prefs?.getBoolean(KEY_DONE, false) ?: false
    } catch (e: RuntimeException) {
        false
    }

    fun markDone() {
        try {
            prefs?.edit()?.putBoolean(KEY_DONE, true)?.apply()
        } catch (e: RuntimeException) {
            // Not persisted; the flow may show again on next launch if no profile exists.
        }
    }

    /** Epoch millis of the profile save made during onboarding, or null if none was recorded. */
    fun profileSavedAt(): Long? = try {
        if (prefs?.contains(KEY_SAVED_AT) == true) prefs.getLong(KEY_SAVED_AT, 0L) else null
    } catch (e: RuntimeException) {
        null
    }

    fun markProfileSaved(epochMillis: Long) {
        try {
            prefs?.edit()?.putLong(KEY_SAVED_AT, epochMillis)?.apply()
        } catch (e: RuntimeException) {
            // Not persisted; the review banner then depends on missing fields only.
        }
    }

    private companion object {
        const val PREFS_NAME = "stepwise_onboarding"
        const val KEY_DONE = "onboarding_done"
        const val KEY_SAVED_AT = "profile_saved_at"
    }
}
