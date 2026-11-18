package com.stepwise.ui

import com.stepwise.data.ProfileState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingLogicTest {

    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_800_000_000_000L

    private fun completeProfile() = ProfileState(
        heightCm = 170.0,
        weightKg = 70.0,
        age = 30,
        sex = "FEMALE",
        strideOverrideCm = null,
    )

    // Step order

    @Test
    fun stepOrderIsWelcomePermissionsProfileDone() {
        assertEquals(
            listOf(
                OnboardingStep.WELCOME,
                OnboardingStep.PERMISSIONS,
                OnboardingStep.PROFILE,
                OnboardingStep.DONE,
            ),
            ONBOARDING_STEPS,
        )
    }

    @Test
    fun nextMovesForwardOneStepAtATime() {
        assertEquals(OnboardingStep.PERMISSIONS, nextStep(OnboardingStep.WELCOME))
        assertEquals(OnboardingStep.PROFILE, nextStep(OnboardingStep.PERMISSIONS))
        assertEquals(OnboardingStep.DONE, nextStep(OnboardingStep.PROFILE))
    }

    @Test
    fun nextAtLastStepStaysOnDone() {
        assertEquals(OnboardingStep.DONE, nextStep(OnboardingStep.DONE))
    }

    @Test
    fun backAtFirstStepStaysOnWelcome() {
        assertEquals(OnboardingStep.WELCOME, previousStep(OnboardingStep.WELCOME))
    }

    @Test
    fun backMovesOneStepBack() {
        assertEquals(OnboardingStep.WELCOME, previousStep(OnboardingStep.PERMISSIONS))
        assertEquals(OnboardingStep.PERMISSIONS, previousStep(OnboardingStep.PROFILE))
        assertEquals(OnboardingStep.PROFILE, previousStep(OnboardingStep.DONE))
    }

    @Test
    fun progressGrowsToOneOnLastStep() {
        assertEquals(0.25f, onboardingProgress(OnboardingStep.WELCOME), 0.0001f)
        assertEquals(1f, onboardingProgress(OnboardingStep.DONE), 0.0001f)
    }

    @Test
    fun onboardingShownOnlyWithoutProfileAndNotDone() {
        assertTrue(shouldShowOnboarding(hasProfile = false, onboardingDone = false))
        assertFalse(shouldShowOnboarding(hasProfile = true, onboardingDone = false))
        assertFalse(shouldShowOnboarding(hasProfile = false, onboardingDone = true))
        assertFalse(shouldShowOnboarding(hasProfile = true, onboardingDone = true))
    }

    // Permission rules

    @Test
    fun deniedActivityBlocksCountingWithMessage() {
        val message = permissionBlockedMessage(activityGranted = false)
        assertTrue(message != null && message.contains("Steps cannot be counted"))
    }

    @Test
    fun grantedActivityHasNoBlockingMessage() {
        assertNull(permissionBlockedMessage(activityGranted = true))
    }

    @Test
    fun optionalPermissionsNeverBlockNext() {
        // Only the activity flag feeds the block message. Next moves on whatever location and
        // notifications say, so both optional permissions can be denied and the flow still advances.
        assertNull(permissionBlockedMessage(activityGranted = true))
        assertEquals(OnboardingStep.PROFILE, nextStep(OnboardingStep.PERMISSIONS))
    }

    // Review banner rule

    @Test
    fun noBannerForRecentCompleteProfile() {
        assertFalse(shouldShowReviewBanner(now - 10 * day, now, emptyList()))
    }

    @Test
    fun bannerAfterMoreThan90Days() {
        assertTrue(shouldShowReviewBanner(now - 91 * day, now, emptyList()))
    }

    @Test
    fun exactly90DaysIsNotOverThreshold() {
        assertFalse(shouldShowReviewBanner(now - 90 * day, now, emptyList()))
    }

    @Test
    fun bannerForMissingFieldsEvenWhenRecent() {
        assertTrue(shouldShowReviewBanner(now - day, now, listOf("Height")))
    }

    @Test
    fun noSavedTimestampAndCompleteProfileHasNoBanner() {
        assertFalse(shouldShowReviewBanner(null, now, emptyList()))
    }

    @Test
    fun completeProfileHasNoMissingFields() {
        assertEquals(emptyList<String>(), missingProfileFields(completeProfile()))
    }

    @Test
    fun zeroValuesCountAsMissing() {
        val blank = completeProfile().copy(heightCm = 0.0, weightKg = 0.0, age = 0, sex = "")
        assertEquals(listOf("Height", "Weight", "Age", "Sex"), missingProfileFields(blank))
    }

    @Test
    fun nullProfileMissesAllRequiredFields() {
        assertEquals(listOf("Height", "Weight", "Age", "Sex"), missingProfileFields(null))
    }

    @Test
    fun missingFieldsTriggerBannerThroughMissingFieldsRule() {
        val blank = completeProfile().copy(age = 0)
        assertTrue(shouldShowReviewBanner(now, now, missingProfileFields(blank)))
    }
}
