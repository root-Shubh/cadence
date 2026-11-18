package com.stepwise.data

import android.content.Context
import android.content.SharedPreferences

data class ProfileState(
    val heightCm: Double,
    val weightKg: Double,
    val age: Int,
    val sex: String,
    val strideOverrideCm: Double?,
)

/** Persists the user profile in SharedPreferences. */
class ProfileStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns the saved profile, or null if none has been saved. */
    fun load(): ProfileState? {
        if (!prefs.contains(KEY_HEIGHT)) return null
        return ProfileState(
            heightCm = Double.fromBits(prefs.getLong(KEY_HEIGHT, 0L)),
            weightKg = Double.fromBits(prefs.getLong(KEY_WEIGHT, 0L)),
            age = prefs.getInt(KEY_AGE, 0),
            sex = prefs.getString(KEY_SEX, "") ?: "",
            strideOverrideCm = if (prefs.contains(KEY_STRIDE))
                Double.fromBits(prefs.getLong(KEY_STRIDE, 0L)) else null,
        )
    }

    /** Validates and saves [state]. Throws IllegalArgumentException with all messages if invalid. */
    fun save(state: ProfileState) {
        val errors = validateProfile(
            heightCm = state.heightCm,
            weightKg = state.weightKg,
            age = state.age,
            sex = state.sex,
            strideOverrideCm = state.strideOverrideCm,
        )
        require(errors.isEmpty()) { errors.joinToString(" ") }

        prefs.edit().apply {
            putLong(KEY_HEIGHT, state.heightCm.toRawBits())
            putLong(KEY_WEIGHT, state.weightKg.toRawBits())
            putInt(KEY_AGE, state.age)
            putString(KEY_SEX, state.sex)
            val stride = state.strideOverrideCm
            if (stride != null) {
                putLong(KEY_STRIDE, stride.toRawBits())
            } else {
                remove(KEY_STRIDE)
            }
        }.apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "stepwise_profile"
        const val KEY_HEIGHT = "height_cm"
        const val KEY_WEIGHT = "weight_kg"
        const val KEY_AGE = "age"
        const val KEY_SEX = "sex"
        const val KEY_STRIDE = "stride_override_cm"
    }
}
