package com.cadence.data

import androidx.room.Entity

/**
 * Steps in one [com.cadence.activity.SLOT_MINUTES]-minute slot of a day (STEPW-63). [slot] is
 * 0 until [com.cadence.activity.SLOTS_PER_DAY], or [com.cadence.activity.UNTIMED_SLOT] for steps
 * with no time of day. The slots of a day add up to its [StepDayEntity.steps].
 */
@Entity(tableName = "step_slots", primaryKeys = ["profileId", "dateIso", "slot"])
data class StepSlotEntity(
    val profileId: String,
    val dateIso: String,
    val slot: Int,
    val steps: Int,
)
