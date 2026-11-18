package com.cadence.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorReportTest {
    @Test
    fun absentSensorIsReportedAsHardwareFact() {
        val lines = describeSensors(
            listOf(SensorInfo(label = "TYPE_STEP_COUNTER", type = 19, present = false)),
        )
        assertEquals(listOf("TYPE_STEP_COUNTER: not present on this device"), lines)
    }

    @Test
    fun presentSensorShowsNameVendorDelayAndWakeType() {
        val lines = describeSensors(
            listOf(
                SensorInfo(
                    label = "TYPE_STEP_DETECTOR",
                    type = 18,
                    present = true,
                    name = "Step detector",
                    vendor = "Samsung",
                    version = 1,
                    minDelayMicros = 0,
                    wakeUp = false,
                ),
            ),
        )
        assertEquals(
            listOf("TYPE_STEP_DETECTOR: Step detector (Samsung, v1), 0 ms min delay, non-wake-up"),
            lines,
        )
    }

    @Test
    fun unknownDelayIsSaidSo() {
        val lines = describeSensors(
            listOf(SensorInfo(label = "TYPE_ACCELEROMETER", type = 1, present = true, name = "Accel", vendor = "X", version = 2, wakeUp = true)),
        )
        assertEquals(listOf("TYPE_ACCELEROMETER: Accel (X, v2), min delay unknown, wake-up"), lines)
    }
}
