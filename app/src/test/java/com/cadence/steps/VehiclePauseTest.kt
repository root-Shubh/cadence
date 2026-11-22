package com.cadence.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehiclePauseTest {

    /** Regression for STEPW-60: a vehicle pause turned counting off for good. */
    @Test
    fun pauseKeepsTheUsersChoiceToCount() {
        val pause = prefsOnVehiclePause(countingOn = true)
        assertTrue(pause.countingOn)
    }

    @Test
    fun pauseClosesTheSessionSoRideStepsAreNotAdded() {
        assertFalse(prefsOnVehiclePause(countingOn = true).sessionOpen)
    }

    @Test
    fun pauseDoesNotTurnCountingOnWhenItWasOff() {
        assertFalse(prefsOnVehiclePause(countingOn = false).countingOn)
    }

    /** Regression for STEPW-60: with the flag kept, a reboot after a pause resumes counting. */
    @Test
    fun bootAfterPauseResumes() {
        val pause = prefsOnVehiclePause(countingOn = true)
        val decision = resumeDecision(countingOn = pause.countingOn, hasPermission = true, hasSource = true)
        assertEquals(ResumeDecision.RESUME, decision)
    }

    @Test
    fun resumeDecisionExplainsEachRefusal() {
        assertEquals(ResumeDecision.OFF_BY_USER, resumeDecision(countingOn = false, hasPermission = true, hasSource = true))
        assertEquals(ResumeDecision.NO_PERMISSION, resumeDecision(countingOn = true, hasPermission = false, hasSource = true))
        assertEquals(ResumeDecision.NO_SOURCE, resumeDecision(countingOn = true, hasPermission = true, hasSource = false))
        assertFalse(ResumeDecision.OFF_BY_USER.resumes)
        assertTrue(ResumeDecision.RESUME.resumes)
    }

    @Test
    fun walkingResumesAfterEnoughStepsInTheWindow() {
        val watch = VehicleResumeWatch(walkSteps = 20, windowMs = 30_000L)
        val results = (0 until 20).map { watch.onStep(1_000L + it * 600L) }
        assertTrue(results.dropLast(1).none { it })
        assertTrue(results.last())
    }

    @Test
    fun scatteredStepsDoNotResume() {
        val watch = VehicleResumeWatch(walkSteps = 20, windowMs = 30_000L)
        // One bump every five seconds: never 20 inside 30 s.
        val any = (0 until 60).any { watch.onStep(it * 5_000L) }
        assertFalse(any)
    }

    @Test
    fun aFastFixBlocksResumeWhileStillRiding() {
        val watch = VehicleResumeWatch(walkSteps = 20, windowMs = 30_000L)
        watch.onFastFix(0L)
        // 25 quick steps inside 30 s of a vehicle-speed fix, for example walking along a train.
        val any = (1..25).any { watch.onStep(it * 500L) }
        assertFalse(any)
    }

    @Test
    fun walkingAfterTheFastFixWindowResumes() {
        val watch = VehicleResumeWatch(walkSteps = 20, windowMs = 30_000L)
        watch.onFastFix(0L)
        val resumed = (0 until 20).map { watch.onStep(31_000L + it * 600L) }
        assertTrue(resumed.last())
    }

    @Test
    fun resetForgetsSteps() {
        val watch = VehicleResumeWatch(walkSteps = 3, windowMs = 30_000L)
        watch.onStep(0L)
        watch.onStep(100L)
        watch.reset()
        assertFalse(watch.onStep(200L))
    }
}
