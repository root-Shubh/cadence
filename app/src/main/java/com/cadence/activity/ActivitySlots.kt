package com.cadence.activity

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.min

/**
 * Steps by time of day (STEPW-63). A day is split into [SLOTS_PER_DAY] slots of [SLOT_MINUTES]
 * minutes. The slots of a day always add up to the day's saved total: steps with no known time go
 * into [UNTIMED_SLOT], so the saved total stays the single source of truth. Pure Kotlin.
 */
const val SLOT_MINUTES: Int = 5
const val SLOTS_PER_DAY: Int = 24 * 60 / SLOT_MINUTES
const val SLOTS_PER_QUARTER: Int = 15 / SLOT_MINUTES
const val SLOTS_PER_HOUR: Int = 60 / SLOT_MINUTES

/**
 * Steps in the day total with no time of day: counted while the service was not running (the
 * hardware counter returns them later in one reading), or saved before this version.
 */
const val UNTIMED_SLOT: Int = -1

/** Slot of [epochMillis] in [zone]'s local day. */
fun slotOf(epochMillis: Long, zone: ZoneId): Int {
    val time = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
    return (time.hour * 60 + time.minute) / SLOT_MINUTES
}

/**
 * Slot to credit steps saved at [nowMillis] for [dayIso]. A write that lands after the day ended
 * (the flush after midnight for the day before) goes in the last slot; a clock moved back gives 0.
 */
fun slotForWrite(dayIso: String, nowMillis: Long, zone: ZoneId): Int {
    val nowDay = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val day = LocalDate.parse(dayIso)
    return when {
        nowDay == day -> slotOf(nowMillis, zone)
        nowDay.isAfter(day) -> SLOTS_PER_DAY - 1
        else -> 0
    }
}

/** What one save adds to the slots: [timed] to the current slot, [untimed] to [UNTIMED_SLOT]. */
data class SlotSplit(val timed: Int, val untimed: Int, val recoveredUsed: Int)

/**
 * Splits one save of the day total into slot additions.
 *
 * - [slotSum]: what the day's slots hold now. [newTotal]: the total being saved.
 * - [previousTotal]: the total stored before this save. The difference is the steps counted since
 *   the last save, which happened now, so they are timed.
 * - [recoveredOwed]: steps known to have been walked while the service was off (the first counter
 *   reading after a restart). Those are untimed even though they arrive now.
 * - Anything else the slots are missing (a total saved before this version, or by a CSV import)
 *   is untimed.
 *
 * After the save the slots add up to [newTotal]. Nothing is added when they already do.
 */
fun planSlotWrite(slotSum: Int, newTotal: Int, previousTotal: Int, recoveredOwed: Int): SlotSplit {
    val missing = newTotal - slotSum
    if (missing <= 0) return SlotSplit(0, 0, 0)
    val counted = (newTotal - previousTotal).coerceIn(0, missing)
    val recovered = min(recoveredOwed.coerceAtLeast(0), counted)
    val timed = counted - recovered
    return SlotSplit(timed = timed, untimed = missing - timed, recoveredUsed = recovered)
}

/**
 * Takes [amount] steps out of [slots] after a vehicle pause rolled them back. Steps are taken from
 * the newest slot at or after [fromSlot] first, then from earlier slots (newest first), then from
 * [UNTIMED_SLOT]. Returns only the slots whose value changed, with their new values.
 */
fun removeSteps(slots: Map<Int, Int>, fromSlot: Int, amount: Int): Map<Int, Int> {
    var left = amount
    val changed = LinkedHashMap<Int, Int>()
    val order = slots.keys.filter { it >= 0 }.sortedDescending()
        .let { keys -> keys.filter { it >= fromSlot } + keys.filter { it < fromSlot } } + UNTIMED_SLOT
    for (slot in order) {
        if (left <= 0) break
        val have = slots[slot] ?: continue
        if (have <= 0) continue
        val take = min(have, left)
        changed[slot] = have - take
        left -= take
    }
    return changed
}

/** Steps per quarter hour (96 values). Untimed steps are left out. */
fun quarterHourTotals(slots: Map<Int, Int>): IntArray = groupTotals(slots, SLOTS_PER_QUARTER)

/** Steps per hour (24 values). Untimed steps are left out. */
fun hourTotals(slots: Map<Int, Int>): IntArray = groupTotals(slots, SLOTS_PER_HOUR)

private fun groupTotals(slots: Map<Int, Int>, size: Int): IntArray {
    val out = IntArray(SLOTS_PER_DAY / size)
    for ((slot, steps) in slots) {
        if (slot in 0 until SLOTS_PER_DAY && steps > 0) out[slot / size] += steps
    }
    return out
}

/** Steps of the day with no time of day. */
fun untimedSteps(slots: Map<Int, Int>): Int = (slots[UNTIMED_SLOT] ?: 0).coerceAtLeast(0)

/** How hard a stretch of time was, for colour. NONE is no steps at all. */
enum class Heat { NONE, LIGHT, MODERATE, BRISK, PEAK }

/**
 * Heat of a quarter hour. Walking at about 100 steps a minute gives 1,500 in 15 minutes, so PEAK
 * is a full quarter hour of steady walking; LIGHT is moving about the house.
 */
const val QUARTER_MODERATE: Int = 300
const val QUARTER_BRISK: Int = 900
const val QUARTER_PEAK: Int = 1_500

fun quarterHeat(steps: Int): Heat = heatOf(steps, QUARTER_MODERATE, QUARTER_BRISK, QUARTER_PEAK)

/** Heat of an hour: PEAK is about 50 minutes of walking. */
const val HOUR_MODERATE: Int = 1_000
const val HOUR_BRISK: Int = 3_000
const val HOUR_PEAK: Int = 5_000

fun hourHeat(steps: Int): Heat = heatOf(steps, HOUR_MODERATE, HOUR_BRISK, HOUR_PEAK)

private fun heatOf(steps: Int, moderate: Int, brisk: Int, peak: Int): Heat = when {
    steps <= 0 -> Heat.NONE
    steps < moderate -> Heat.LIGHT
    steps < brisk -> Heat.MODERATE
    steps < peak -> Heat.BRISK
    else -> Heat.PEAK
}

/** A slot counts as walking with at least this many steps (20 a minute on average; tuned on a day of real walks). */
const val WALK_SLOT_MIN_STEPS: Int = 100

/** Quiet slots allowed inside one walk, for example waiting at a crossing. */
const val WALK_MAX_GAP_SLOTS: Int = 1

/** Shorter stretches are not listed as walks. */
const val WALK_MIN_STEPS: Int = 200

/** One walk: slots [startSlot] until [endSlot] (exclusive), with [steps] in them. */
data class Walk(val startSlot: Int, val endSlot: Int, val steps: Int) {
    val minutes: Int get() = (endSlot - startSlot) * SLOT_MINUTES
    val startMinute: Int get() = startSlot * SLOT_MINUTES
    val endMinute: Int get() = endSlot * SLOT_MINUTES
}

/**
 * The day's walks: runs of walking slots, allowing [WALK_MAX_GAP_SLOTS] quiet slots inside a run.
 * The times are accurate to [SLOT_MINUTES] minutes. Untimed steps are not part of any walk.
 */
fun findWalks(slots: Map<Int, Int>): List<Walk> {
    val walks = ArrayList<Walk>()
    var start = -1
    var lastActive = -1
    fun close() {
        if (start < 0) return
        var steps = 0
        for (s in start..lastActive) steps += (slots[s] ?: 0).coerceAtLeast(0)
        if (steps >= WALK_MIN_STEPS) walks.add(Walk(start, lastActive + 1, steps))
        start = -1
    }
    for (slot in 0 until SLOTS_PER_DAY) {
        if ((slots[slot] ?: 0) >= WALK_SLOT_MIN_STEPS) {
            if (start >= 0 && slot - lastActive - 1 > WALK_MAX_GAP_SLOTS) close()
            if (start < 0) start = slot
            lastActive = slot
        }
    }
    close()
    return walks
}

/** Part of the day a minute of the day falls in, for the walk list. */
fun periodOfDay(minuteOfDay: Int): String = when (minuteOfDay / 60) {
    in 5..11 -> "Morning"
    in 12..16 -> "Afternoon"
    in 17..20 -> "Evening"
    else -> "Night"
}

/** "HH:mm" for a minute of the day; 1440 shows as 24:00. */
fun clockOf(minuteOfDay: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

/** Index of the hour with the most steps across [days] (each 24 hour totals), or null with none. */
fun busiestHour(days: List<IntArray>): Int? {
    val sums = IntArray(24)
    for (day in days) for (h in 0 until min(24, day.size)) sums[h] += day[h]
    val best = sums.indices.maxByOrNull { sums[it] } ?: return null
    return if (sums[best] > 0) best else null
}
