package com.cadence.ui

import com.cadence.data.ProfileState

/** Steps of the first-launch flow, in the order the user sees them. */
enum class OnboardingStep { WELCOME, PERMISSIONS, PROFILE, DONE }

/** All steps in display order. */
val ONBOARDING_STEPS: List<OnboardingStep> = OnboardingStep.values().toList()

/** Next step in order. The last step stays where it is. */
fun nextStep(step: OnboardingStep): OnboardingStep = ONBOARDING_STEPS.getOrElse(step.ordinal + 1) { step }

/** Previous step in order. The first step stays where it is. */
fun previousStep(step: OnboardingStep): OnboardingStep = ONBOARDING_STEPS.getOrElse(step.ordinal - 1) { step }

/** Progress through the flow, from above 0 on the first step to 1 on the last. */
fun onboardingProgress(step: OnboardingStep): Float = (step.ordinal + 1).toFloat() / ONBOARDING_STEPS.size

/** The flow is shown only when no profile exists and onboarding was not finished before. */
fun shouldShowOnboarding(hasProfile: Boolean, onboardingDone: Boolean): Boolean =
    !hasProfile && !onboardingDone

/**
 * Message shown when Physical activity is denied, or null when steps can be counted.
 * Only physical activity can block counting; location and notifications never do.
 */
fun permissionBlockedMessage(activityGranted: Boolean): String? =
    if (activityGranted) null else "Steps cannot be counted until Physical activity is allowed."

/** Review banner threshold: a profile saved more than this long ago is due a check. */
const val REVIEW_AFTER_MILLIS: Long = 90L * 24L * 60L * 60L * 1000L

/**
 * Required profile fields that are still missing (zero or blank). Empty when the profile is complete.
 * A null profile counts as every field missing.
 */
fun missingProfileFields(profile: ProfileState?): List<String> {
    if (profile == null) return listOf("Height", "Weight", "Age", "Sex")
    val missing = mutableListOf<String>()
    if (profile.heightCm <= 0.0) missing += "Height"
    if (profile.weightKg <= 0.0) missing += "Weight"
    if (profile.age <= 0) missing += "Age"
    if (profile.sex !in setOf("MALE", "FEMALE", "OTHER")) missing += "Sex"
    return missing
}

/**
 * True when the Profile screen should show the review banner: some required field is missing,
 * or the profile was saved more than [REVIEW_AFTER_MILLIS] before [nowMillis].
 * A null [savedAtMillis] (no onboarding save recorded) only counts through missing fields.
 */
fun shouldShowReviewBanner(savedAtMillis: Long?, nowMillis: Long, missingFields: List<String>): Boolean =
    missingFields.isNotEmpty() ||
        (savedAtMillis != null && nowMillis - savedAtMillis > REVIEW_AFTER_MILLIS)
