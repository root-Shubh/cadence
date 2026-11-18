package com.cadence.ui

import com.cadence.data.validateProfile
import com.cadence.domain.Profile
import com.cadence.domain.Sex
import com.cadence.domain.bmrKcal
import com.cadence.domain.computeDay
import kotlin.math.roundToInt

/** Live estimates shown on the profile form, in whole kcal per day. */
data class ProfileEstimate(val bmrKcal: Int, val dailyRestKcal: Int)

/** Field names that start the validation messages from [validateProfile]. */
private val PROFILE_FIELDS = listOf("Height", "Weight", "Age", "Sex", "Stride")

/**
 * Estimates for the "Your numbers" card. Returns null unless the four body fields pass
 * [validateProfile]. Values come from the domain functions (no formula is repeated here).
 */
fun profileEstimate(
    heightCm: Double?,
    weightKg: Double?,
    age: Int?,
    sex: String?,
): ProfileEstimate? {
    if (validateProfile(heightCm, weightKg, age, sex, null).isNotEmpty()) return null
    val height = heightCm ?: return null
    val weight = weightKg ?: return null
    val years = age ?: return null
    val sexValue = sex?.let { runCatching { Sex.valueOf(it) }.getOrNull() } ?: return null
    val profile = runCatching { Profile(height, weight, years, sexValue) }.getOrNull() ?: return null
    return ProfileEstimate(
        bmrKcal = bmrKcal(weight, height, years, sexValue).roundToInt(),
        dailyRestKcal = computeDay(profile, steps = 0).rmrKcal.roundToInt(),
    )
}

/** The first validation message for [field] (for example "Height"), or null. */
fun fieldError(errors: List<String>, field: String): String? =
    errors.firstOrNull { it.startsWith(field) }

/** Messages that do not belong to any single form field. */
fun ungroupedErrors(errors: List<String>): List<String> =
    errors.filter { message -> PROFILE_FIELDS.none { message.startsWith(it) } }
