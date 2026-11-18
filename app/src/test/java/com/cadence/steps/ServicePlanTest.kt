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
}
