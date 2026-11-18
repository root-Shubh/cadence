package com.cadence.steps

import android.content.Context

/**
 * Saves and loads the GPS-learned stride in SharedPreferences "cadence_stride":
 * stride_cm (the running average), samples and walks.
 */
class StrideStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Restores the estimator from storage. A missing or unreadable value starts from nothing. */
    fun load(): StrideEstimator {
        val cm = if (prefs.contains(KEY_STRIDE_CM)) prefs.getFloat(KEY_STRIDE_CM, 0f).toDouble() else null
        return StrideEstimator(
            initialCm = cm,
            initialSamples = prefs.getInt(KEY_SAMPLES, 0),
            initialWalks = prefs.getInt(KEY_WALKS, 0),
        )
    }

    /** Stores the estimator's current state. */
    fun save(estimator: StrideEstimator) {
        val editor = prefs.edit()
            .putInt(KEY_SAMPLES, estimator.samples)
            .putInt(KEY_WALKS, estimator.walks)
        val cm = estimator.learnedCm
        if (cm != null) editor.putFloat(KEY_STRIDE_CM, cm.toFloat()) else editor.remove(KEY_STRIDE_CM)
        editor.apply()
    }

    /** The learned stride in cm for distance, or null when not yet usable. */
    fun usableCm(): Double? = load().usableCm()

    companion object {
        const val PREFS_NAME = "cadence_stride"
        const val KEY_STRIDE_CM = "stride_cm"
        const val KEY_SAMPLES = "samples"
        const val KEY_WALKS = "walks"
    }
}
