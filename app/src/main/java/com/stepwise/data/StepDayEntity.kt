package com.stepwise.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per calendar day. [dateIso] is the local date in yyyy-MM-dd form. */
@Entity(tableName = "step_days")
data class StepDayEntity(
    @PrimaryKey val dateIso: String,
    val steps: Int,
    val distanceM: Double,
    val activeKcal: Double,
    val bmrKcal: Double,
    val rmrKcal: Double,
    val totalKcal: Double,
)
