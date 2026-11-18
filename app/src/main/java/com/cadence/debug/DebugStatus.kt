package com.cadence.debug

/**
 * Status lines for the Debug panel, built from the values the service stores. Pure so the
 * wording and the "none yet" and age cases can be unit-tested.
 */
fun debugStatusLines(
    countingOn: Boolean,
    sensorMissing: Boolean,
    eventsReceived: Long,
    lastEventMillis: Long,
    lastRaw: Long,
    nowMillis: Long,
): List<String> {
    val lastEvent = if (lastEventMillis <= 0L) {
        "none yet"
    } else {
        "${formatAge(nowMillis - lastEventMillis)} ago (raw $lastRaw)"
    }
    return listOf(
        "Counting switched on: ${yesNo(countingOn)}",
        "Step sensor missing flag: ${yesNo(sensorMissing)}",
        "Counter events received: $eventsReceived",
        "Last counter event: $lastEvent",
    )
}

/** Age as whole seconds below a minute, then minutes and seconds. Negative ages read as zero. */
internal fun formatAge(ageMillis: Long): String {
    val seconds = (ageMillis.coerceAtLeast(0L)) / 1000L
    return if (seconds < 60L) "${seconds}s" else "${seconds / 60L}m ${seconds % 60L}s"
}

private fun yesNo(value: Boolean): String = if (value) "yes" else "no"
