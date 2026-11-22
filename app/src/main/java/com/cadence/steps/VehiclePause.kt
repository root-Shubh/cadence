package com.cadence.steps

/**
 * Detector steps within [RESUME_WINDOW_MS] that end a vehicle pause when GPS cannot. Indoors, or
 * after a ride that ends underground, GPS never confirms the slow speed, so walking ends the pause.
 * 20 steps is about 12 s of walking, more than the odd step a bump in a moving vehicle gives.
 */
const val RESUME_WALK_STEPS: Int = 20

/** Window for [RESUME_WALK_STEPS], and how long a fast GPS fix blocks a step-based resume. */
const val RESUME_WINDOW_MS: Long = 30_000L

/**
 * The saved counting state while a vehicle pause runs. The user's choice to count is kept, so a
 * reboot or a killed process resumes counting. The session is closed, so the steps the hardware
 * counter adds during the ride are not added when counting resumes (fresh baseline).
 */
data class PausePrefs(val countingOn: Boolean, val sessionOpen: Boolean)

/** See [PausePrefs]. [countingOn] is the user's saved choice before the pause. */
fun prefsOnVehiclePause(countingOn: Boolean): PausePrefs =
    PausePrefs(countingOn = countingOn, sessionOpen = false)

/**
 * Decides when walking ends a vehicle pause. Feed it every detector step and every accurate GPS
 * fix at vehicle speed. Pure Kotlin, not thread-safe: the service uses it on the main thread.
 */
class VehicleResumeWatch(
    private val walkSteps: Int = RESUME_WALK_STEPS,
    private val windowMs: Long = RESUME_WINDOW_MS,
) {
    private val stepTimes = ArrayDeque<Long>()
    private var lastFastMillis: Long? = null

    /** An accurate GPS fix at vehicle speed: still riding, so the steps so far do not count. */
    fun onFastFix(nowMillis: Long) {
        lastFastMillis = nowMillis
        stepTimes.clear()
    }

    /** One detector step. Returns true when the user is walking again and counting should resume. */
    fun onStep(nowMillis: Long): Boolean {
        stepTimes.addLast(nowMillis)
        while (stepTimes.isNotEmpty() && nowMillis - stepTimes.first() > windowMs) stepTimes.removeFirst()
        val fast = lastFastMillis
        if (fast != null && nowMillis - fast < windowMs) return false
        return stepTimes.size >= walkSteps
    }

    /** Forgets everything, for example when a new pause starts. */
    fun reset() {
        stepTimes.clear()
        lastFastMillis = null
    }
}

/** Why counting was or was not resumed at app launch or boot. Logged, so a silent stop is visible. */
enum class ResumeDecision(val resumes: Boolean, val reason: String) {
    RESUME(true, "counting restarted"),
    OFF_BY_USER(false, "counting is off (stopped by the user, or never started)"),
    NO_PERMISSION(false, "counting is on but the activity permission is missing"),
    NO_SOURCE(false, "counting is on but the phone has no step sensor or accelerometer"),
}

/** Pure version of the checks in [resumeCountingIfNeeded]. */
fun resumeDecision(countingOn: Boolean, hasPermission: Boolean, hasSource: Boolean): ResumeDecision = when {
    !countingOn -> ResumeDecision.OFF_BY_USER
    !hasPermission -> ResumeDecision.NO_PERMISSION
    !hasSource -> ResumeDecision.NO_SOURCE
    else -> ResumeDecision.RESUME
}
