package com.stepwise.steps

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the Today screen can say about step counting right now. */
sealed class CountingStatus {
    /** ACTIVITY_RECOGNITION is not granted, so no steps can be counted. */
    object NoPermission : CountingStatus()

    /** The device has no step counter. */
    object NoSensor : CountingStatus()

    /** The service runs but no sensor event has arrived yet. */
    object WaitingForSteps : CountingStatus()

    /** The last sensor event was recent. */
    class Counting(val lastEventMillis: Long) : CountingStatus()

    /** The last sensor event is older than [COUNTING_WINDOW_MILLIS]. */
    class Idle(val lastEventMillis: Long) : CountingStatus()

    companion object {
        const val COUNTING_WINDOW_MILLIS: Long = 2 * 60 * 1000L
    }
}

/**
 * Pure status decision. [lastEventMillis] is the wall-clock time of the last sensor event,
 * or null when none has been recorded.
 */
fun countingStatus(
    hasPermission: Boolean,
    hasSensor: Boolean,
    lastEventMillis: Long?,
    nowMillis: Long,
): CountingStatus = when {
    !hasPermission -> CountingStatus.NoPermission
    !hasSensor -> CountingStatus.NoSensor
    lastEventMillis == null -> CountingStatus.WaitingForSteps
    nowMillis - lastEventMillis <= CountingStatus.COUNTING_WINDOW_MILLIS ->
        CountingStatus.Counting(lastEventMillis)
    else -> CountingStatus.Idle(lastEventMillis)
}

/** Plain-words label for the Today screen. Idle times are shown as HH:mm in [zone]. */
fun describeCountingStatus(status: CountingStatus, zone: ZoneId = ZoneId.systemDefault()): String =
    when (status) {
        CountingStatus.NoPermission -> "Physical activity permission is off"
        CountingStatus.NoSensor -> "This phone has no step counter"
        CountingStatus.WaitingForSteps -> "Waiting for your first step"
        is CountingStatus.Counting -> "Counting"
        is CountingStatus.Idle -> {
            val time = Instant.ofEpochMilli(status.lastEventMillis)
                .atZone(zone)
                .format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
            "Idle since $time"
        }
    }
