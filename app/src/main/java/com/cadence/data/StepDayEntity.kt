package com.cadence.data

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * One row per profile and calendar day. [dateIso] is the local date in yyyy-MM-dd form.
 * [profileId] is last so positional constructor calls keep their meaning.
 */
@Entity(tableName = "step_days", primaryKeys = ["profileId", "dateIso"])
data class StepDayEntity(
    val dateIso: String,
    val steps: Int,
    val distanceM: Double,
    val activeKcal: Double,
    val bmrKcal: Double,
    val rmrKcal: Double,
    val totalKcal: Double,
    @ColumnInfo(defaultValue = "'default'")
    val profileId: String = DEFAULT_PROFILE_ID,
)
