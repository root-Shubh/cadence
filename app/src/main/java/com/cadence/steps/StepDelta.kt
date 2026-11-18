package com.cadence.steps

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure step-counting logic. No Android dependencies.
 */
object StepDelta {

    private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Returns the steps added since [lastRaw].
     *
     * - [lastRaw] null: the first reading is a baseline, returns 0.
     * - [currentRaw] >= [lastRaw]: returns the difference.
     * - [currentRaw] < [lastRaw]: the counter was reset (device reboot), so returns
     *   [currentRaw], the steps since reboot.
     */
    fun nextDelta(lastRaw: Long?, currentRaw: Long): Long {
        if (lastRaw == null) return 0L
        return if (currentRaw >= lastRaw) currentRaw - lastRaw else currentRaw
    }

    /**
     * Steps to add to the counter total for one TYPE_STEP_COUNTER reading.
     *
     * - [lastRaw] null: this is the first reading of a session, so it becomes the baseline. The
     *   raw value also holds steps from before counting started, so none of it is added. Only
     *   [lead], the detector steps seen since counting started and before this reading, is
     *   credited, because those steps are inside the reading.
     * - Otherwise: the difference from [lastRaw], as [nextDelta].
     */
    fun counterDelta(lastRaw: Long?, currentRaw: Long, lead: Long): Long =
        if (lastRaw == null) lead.coerceAtLeast(0L) else nextDelta(lastRaw, currentRaw)

    /** Local calendar day for [epochMillis] in [zone], formatted as yyyy-MM-dd. */
    fun dateIsoFor(epochMillis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().format(DAY_FORMAT)
}
