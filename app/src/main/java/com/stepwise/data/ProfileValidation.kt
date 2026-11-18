package com.stepwise.data

private val SEXES = setOf("MALE", "FEMALE", "OTHER")

/**
 * Validates profile inputs. Returns human-readable messages; an empty list means valid.
 * Pure Kotlin: no Android dependencies.
 */
fun validateProfile(
    heightCm: Double?,
    weightKg: Double?,
    age: Int?,
    sex: String?,
    strideOverrideCm: Double?,
): List<String> {
    val errors = mutableListOf<String>()

    if (heightCm == null) {
        errors += "Height is required."
    } else if (heightCm !in 100.0..250.0) {
        errors += "Height must be between 100 and 250 cm."
    }

    if (weightKg == null) {
        errors += "Weight is required."
    } else if (weightKg !in 30.0..300.0) {
        errors += "Weight must be between 30 and 300 kg."
    }

    if (age == null) {
        errors += "Age is required."
    } else if (age !in 10..100) {
        errors += "Age must be between 10 and 100 years."
    }

    if (sex == null) {
        errors += "Sex is required."
    } else if (sex !in SEXES) {
        errors += "Sex must be MALE, FEMALE or OTHER."
    }

    if (strideOverrideCm != null && strideOverrideCm !in 30.0..120.0) {
        errors += "Stride override must be between 30 and 120 cm."
    }

    return errors
}
