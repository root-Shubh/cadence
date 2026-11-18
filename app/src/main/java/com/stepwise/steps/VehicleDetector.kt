package com.stepwise.steps

/** Speed above which the user counts as moving at bike or car speed, in km/h. */
const val VEHICLE_SPEED_KMH: Double = 20.0

/** How long the speed must stay above (or below) the threshold before the state changes. */
const val SUSTAINED_MS: Long = 20_000L

/** Events from [VehicleDetector]. */
sealed interface VehicleEvent

/** Speed has been above the threshold for [SUSTAINED_MS]. [startTimeMillis] is when it first went above. */
data class VehicleStarted(val startTimeMillis: Long) : VehicleEvent

/** Speed has been below the threshold for [SUSTAINED_MS] after a vehicle was detected. */
object VehicleEnded : VehicleEvent

/**
 * Turns a stream of GPS speeds into vehicle start and end events. Pure: no Android imports.
 * Feed it km/h readings with their timestamps in ascending order.
 */
class VehicleDetector(
    private val thresholdKmh: Double = VEHICLE_SPEED_KMH,
    private val sustainedMs: Long = SUSTAINED_MS,
) {
    private var inVehicle = false
    private var fastSince: Long? = null
    private var slowSince: Long? = null

    /** Forgets the current state, for example when counting resumes. */
    fun reset() {
        inVehicle = false
        fastSince = null
        slowSince = null
    }

    fun onSpeed(kmh: Double, timeMillis: Long): VehicleEvent? {
        return if (!inVehicle) {
            slowSince = null
            if (kmh < thresholdKmh) {
                fastSince = null
                null
            } else {
                val since = fastSince ?: timeMillis.also { fastSince = it }
                if (timeMillis - since >= sustainedMs) {
                    inVehicle = true
                    fastSince = null
                    VehicleStarted(since)
                } else {
                    null
                }
            }
        } else {
            fastSince = null
            if (kmh >= thresholdKmh) {
                slowSince = null
                null
            } else {
                val since = slowSince ?: timeMillis.also { slowSince = it }
                if (timeMillis - since >= sustainedMs) {
                    inVehicle = false
                    slowSince = null
                    VehicleEnded
                } else {
                    null
                }
            }
        }
    }
}

/**
 * Steps to take back after a vehicle started at [startTimeMillis]. [log] holds (timeMillis,
 * stored total after that write) pairs. The baseline is the total at or just before the start.
 * The result is never negative and never more than [currentTotal]. With no entry at or before
 * the start, nothing is removed.
 */
fun stepsToRemove(log: List<Pair<Long, Int>>, startTimeMillis: Long, currentTotal: Int): Int {
    val baseline = log
        .filter { it.first <= startTimeMillis }
        .maxByOrNull { it.first }
        ?.second
        ?: return 0
    return (currentTotal - baseline).coerceIn(0, currentTotal.coerceAtLeast(0))
}

/**
 * Adds (timeMillis, total) to [log] and keeps only entries at or after [keepFromMillis]
 * (the later of the start of today and two hours before now).
 */
fun appendToVehicleLog(
    log: List<Pair<Long, Int>>,
    timeMillis: Long,
    total: Int,
    keepFromMillis: Long,
): List<Pair<Long, Int>> = (log + (timeMillis to total)).filter { it.first >= keepFromMillis }

/** Two hours, the longest history kept for step rollback. */
const val VEHICLE_LOG_WINDOW_MS: Long = 2L * 60L * 60L * 1000L

/** Encodes a log as "time:total;time:total". */
fun encodeVehicleLog(log: List<Pair<Long, Int>>): String =
    log.joinToString(";") { "${it.first}:${it.second}" }

/** Decodes [encodeVehicleLog] output. Bad entries are skipped. */
fun decodeVehicleLog(encoded: String?): List<Pair<Long, Int>> {
    if (encoded.isNullOrEmpty()) return emptyList()
    return encoded.split(";").mapNotNull { entry ->
        val parts = entry.split(":")
        if (parts.size != 2) return@mapNotNull null
        val time = parts[0].toLongOrNull() ?: return@mapNotNull null
        val total = parts[1].toIntOrNull() ?: return@mapNotNull null
        time to total
    }
}
