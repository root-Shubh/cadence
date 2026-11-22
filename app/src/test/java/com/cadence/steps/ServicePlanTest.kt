package com.cadence.steps

import org.junit.Assert.assertEquals
import org.junit.Test

class ServicePlanTest {

    @Test
    fun countsWhenPermissionAndSensorPresent() {
        assertEquals(ServicePlan.COUNT, servicePlan(hasPermission = true, hasSensor = true))
    }

    /** Regression for STEPW-23: no step counter must not crash the app. */
    @Test
    fun noSensorGivesNoSensorPlan() {
        assertEquals(ServicePlan.NO_SENSOR, servicePlan(hasPermission = true, hasSensor = false))
    }

    @Test
    fun missingPermissionWinsOverSensor() {
        assertEquals(ServicePlan.NO_PERMISSION, servicePlan(hasPermission = false, hasSensor = true))
        assertEquals(ServicePlan.NO_PERMISSION, servicePlan(hasPermission = false, hasSensor = false))
    }

    @Test
    fun noStepCounterWithAccelerometerGivesAccelerometerPlan() {
        assertEquals(
            ServicePlan.ACCELEROMETER,
            servicePlan(hasPermission = true, hasSensor = false, hasAccelerometer = true),
        )
    }

    @Test
    fun stepCounterWinsOverAccelerometer() {
        assertEquals(
            ServicePlan.COUNT,
            servicePlan(hasPermission = true, hasSensor = true, hasAccelerometer = true),
        )
    }

    @Test
    fun noSensorAndNoAccelerometerGivesNoSensorPlan() {
        assertEquals(
            ServicePlan.NO_SENSOR,
            servicePlan(hasPermission = true, hasSensor = false, hasAccelerometer = false),
        )
    }

    @Test
    fun missingPermissionWinsOverAccelerometer() {
        assertEquals(
            ServicePlan.NO_PERMISSION,
            servicePlan(hasPermission = false, hasSensor = false, hasAccelerometer = true),
        )
    }

    /** Regression for STEPW-62: the accelerometer ran beside a working detector and showed phantom steps. */
    @Test
    fun accelerometerStaysOffWhileTheDetectorRuns() {
        assertEquals(false, accelerometerNeeded(detectorRunning = true, hasAccelerometer = true))
    }

    @Test
    fun accelerometerIsTheFallbackWithoutADetector() {
        assertEquals(true, accelerometerNeeded(detectorRunning = false, hasAccelerometer = true))
        assertEquals(false, accelerometerNeeded(detectorRunning = false, hasAccelerometer = false))
    }

    /** Regression for STEPW-61: every step was logged, so the log held about 25 minutes. */
    @Test
    fun onlyTheFirstStepEventsAreLogged() {
        assertEquals(true, shouldLogStepEvent(1))
        assertEquals(true, shouldLogStepEvent(STEP_EVENT_LOG_LIMIT))
        assertEquals(false, shouldLogStepEvent(STEP_EVENT_LOG_LIMIT + 1))
        assertEquals(false, shouldLogStepEvent(0))
    }
}
